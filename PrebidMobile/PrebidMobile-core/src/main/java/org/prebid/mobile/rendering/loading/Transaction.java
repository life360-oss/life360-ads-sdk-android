/*
 *    Copyright 2018-2021 Prebid.org, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.prebid.mobile.rendering.loading;

import android.content.Context;
import org.prebid.mobile.LogUtil;
import org.prebid.mobile.PrebidMobile;
import org.prebid.mobile.api.exceptions.AdException;
import org.prebid.mobile.rendering.models.AbstractCreative;
import org.prebid.mobile.rendering.models.CreativeModel;
import org.prebid.mobile.rendering.models.CreativeModelsMaker;
import org.prebid.mobile.rendering.sdk.JSLibraryManager;
import org.prebid.mobile.rendering.session.manager.OmAdSessionManager;
import org.prebid.mobile.rendering.views.interstitial.InterstitialManager;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Transaction {

    public static final String TAG = Transaction.class.getSimpleName();

    private List<CreativeFactory> creativeFactories;
    private Iterator<CreativeFactory> creativeFactoryIterator;
    private CreativeFactory currentCreativeFactory;

    private List<CreativeModel> creativeModels;

    private WeakReference<Context> contextReference;
    private Listener listener;
    private OmAdSessionManager omAdSessionManager;
    private final InterstitialManager interstitialManager;

    private String transactionState;
    private String loaderIdentifier;

    private long transactionCreateTime;

    /**
     * Wall clock at which the running retry chain's shared budget expires, or 0 when no chain is running.
     * <p>
     * One budget for the whole chain rather than a fresh deadline per attempt, so the worst-case wait for a
     * terminal failure stays at twice the configured render deadline instead of scaling with
     * {@link PrebidMobile#getCreativeFactoryTimeoutRetryCount()}. The publisher's slot is blocked for that
     * whole wait.
     */
    private long retryBudgetDeadline;

    public interface Listener {

        void onTransactionSuccess(Transaction transaction);

        void onTransactionFailure(
                AdException e,
                String identifier
        );

    }

    private Transaction(Context context, List<CreativeModel> creativeModels,
                        String transactionState,
                        InterstitialManager interstitialManager,
                        Listener listener)
    throws AdException {
        if (context == null) {
            throw new AdException(AdException.INTERNAL_ERROR, "Transaction - Context is null");
        }

        if (creativeModels == null || creativeModels.isEmpty()) {
            throw new AdException(AdException.INTERNAL_ERROR, "Transaction - Creative models is empty");
        }

        if (listener == null) {
            throw new AdException(AdException.INTERNAL_ERROR, "Transaction - Listener is null");
        }

        contextReference = new WeakReference<>(context);
        this.creativeModels = creativeModels;
        checkForBuiltInVideo();
        this.transactionState = transactionState;
        this.listener = listener;
        this.interstitialManager = interstitialManager;

        omAdSessionManager = OmAdSessionManager.createNewInstance(JSLibraryManager.getInstance(context));

        creativeFactories = new ArrayList<>();
    }

    public static Transaction createTransaction(Context context, CreativeModelsMaker.Result result,
                                                InterstitialManager interstitialManager, Listener listener)
    throws AdException {
        Transaction transaction = new Transaction(
            context,
            result.creativeModels,
            result.transactionState,
            interstitialManager,
            listener);
        transaction.setTransactionCreateTime(System.currentTimeMillis());
        transaction.setLoaderIdentifier(result.loaderIdentifier);

        return transaction;
    }

    private void checkForBuiltInVideo() {
        try {
            if (creativeModels != null && creativeModels.size() > 1) {
                CreativeModel creativeModel = creativeModels.get(0);
                boolean isBannerVideo = creativeModel.getAdConfiguration().isBuiltInVideo();
                if (isBannerVideo) {
                    CreativeModel possibleEndCard = creativeModels.get(1);
                    possibleEndCard.getAdConfiguration().setBuiltInVideo(true);
                }
            }
        }
        catch (Exception e) {
            LogUtil.error(TAG, "Failed to check for built in video override");
        }
    }

    public String getTransactionState() {
        return transactionState;
    }

    public void startCreativeFactories() {
        try {
            // Initialize list of CreativeFactories
            creativeFactories.clear();
            for (CreativeModel creativeModel : creativeModels) {
                CreativeFactoryListener factoryListener = new CreativeFactoryListener(this);
                CreativeFactory creativeFactory = new CreativeFactory(contextReference.get(),
                        creativeModel,
                        factoryListener,
                        omAdSessionManager,
                        interstitialManager
                );
                factoryListener.setFactory(creativeFactory);
                creativeFactories.add(creativeFactory);
            }

            // Start first CreativeFactory, if any
            // On success, the CreativeFactoryListener will start the next CreativeFactory
            creativeFactoryIterator = creativeFactories.iterator();
            startNextCreativeFactory();
        }
        catch (AdException e) {
            listener.onTransactionFailure(e, loaderIdentifier);
        }
    }

    public void destroy() {
        stopOmAdSession();

        for (CreativeFactory creativeFactory : creativeFactories) {
            creativeFactory.destroy();
        }
    }

    private boolean startNextCreativeFactory() {
        // No CreativeFactory to start
        if (creativeFactoryIterator == null || !creativeFactoryIterator.hasNext()) {
            return false;
        }
        currentCreativeFactory = creativeFactoryIterator.next();
        // Each model gets its own retry chain, and so its own budget.
        retryBudgetDeadline = 0;
        currentCreativeFactory.start();
        return true;
    }

    private CreativeFactory createFactoryForModel(
            CreativeModel creativeModel,
            int remainingRetryAttempts,
            long timeoutBudgetMs
    ) throws AdException {
        CreativeFactoryListener factoryListener = new CreativeFactoryListener(this, remainingRetryAttempts);
        CreativeFactory factory = new CreativeFactory(contextReference.get(),
                creativeModel,
                factoryListener,
                omAdSessionManager,
                interstitialManager,
                timeoutBudgetMs
        );
        factoryListener.setFactory(factory);
        return factory;
    }

    /**
     * Replaces the current CreativeFactory with a fresh one for the same model after a render timeout.
     *
     * @param remainingRetryAttempts retries left <em>after</em> this one, carried into the replacement's
     *                               listener so the budget actually decreases across a retry chain.
     * @param timeout                the render timeout that triggered this retry, reported as the terminal
     *                               failure if the chain's time budget is already spent.
     */
    private void retryCurrentFactory(int remainingRetryAttempts, AdException timeout) {
        LogUtil.warning(TAG, "Creative factory retry. Attempts remaining after this one: " + remainingRetryAttempts);

        // A retry needs the model the previous factory was built from, which is only reachable through its
        // creative. Both are nullable, so both are checked.
        CreativeFactory previousFactory = currentCreativeFactory;
        AbstractCreative previousCreative = previousFactory != null ? previousFactory.getCreative() : null;
        CreativeModel model = previousCreative != null ? previousCreative.getCreativeModel() : null;
        if (model == null) {
            LogUtil.error(TAG, "Creative factory retry failed. No creative model to retry.");
            listener.onTransactionFailure(
                    new AdException(AdException.INTERNAL_ERROR, "Creative factory retry failed. No creative model to retry."),
                    loaderIdentifier
            );
            destroy();
            return;
        }

        // The replacement occupies the old factory's slot so destroy() can still reach it. It must replace by
        // index, not append: the list's size and indices are TransactionManager's bookkeeping (see
        // TransactionManager#hasNextCreative), and appending would invalidate creativeFactoryIterator.
        int slot = creativeFactories.indexOf(previousFactory);
        if (slot < 0) {
            // The current factory always comes from creativeFactories, so this means the two have diverged.
            // Fail rather than start a retry that destroy() could never reach.
            LogUtil.error(TAG, "Creative factory retry failed. Current factory is not tracked.");
            listener.onTransactionFailure(
                    new AdException(AdException.INTERNAL_ERROR, "Creative factory retry failed. Current factory is not tracked."),
                    loaderIdentifier
            );
            destroy();
            return;
        }
        // Seed the chain's budget from the deadline a first attempt would have used, then hand each retry
        // only what is left of it. Once it is spent the timeout that got us here is the terminal failure.
        long now = System.currentTimeMillis();
        if (retryBudgetDeadline == 0) {
            retryBudgetDeadline = now + CreativeFactory.configuredTimeoutMs(model);
        }
        long remainingBudgetMs = retryBudgetDeadline - now;
        if (remainingBudgetMs <= 0) {
            LogUtil.warning(TAG, "Creative factory retry budget spent. Failing.");
            listener.onTransactionFailure(timeout, loaderIdentifier);
            destroy();
            return;
        }

        previousFactory.destroy();

        try {
            currentCreativeFactory = createFactoryForModel(model, remainingRetryAttempts, remainingBudgetMs);
            creativeFactories.set(slot, currentCreativeFactory);
            currentCreativeFactory.start();
        } catch (AdException e) {
            listener.onTransactionFailure(e, loaderIdentifier);
            destroy();
        }
    }

    private void stopOmAdSession() {
        if (omAdSessionManager == null) {
            LogUtil.error(TAG, "Failed to stopOmAdSession. OmAdSessionManager is null");
            return;
        }

        omAdSessionManager.stopAdSession();
        omAdSessionManager = null;
    }

    public List<CreativeFactory> getCreativeFactories() {
        return creativeFactories;
    }

    public String getLoaderIdentifier() {
        return loaderIdentifier;
    }

    public void setLoaderIdentifier(String loaderIdentifier) {
        this.loaderIdentifier = loaderIdentifier;
    }

    public long getTransactionCreateTime() {
        return transactionCreateTime;
    }

    public void setTransactionCreateTime(long transactionCreateTime) {
        this.transactionCreateTime = transactionCreateTime;
    }

    /**
     * Listens for when CreativeFactory is done making a creative
     * When all CreativeFactory's are done, relays that back to Transaction's Listener
     */
    public static class CreativeFactoryListener implements CreativeFactory.Listener {

        private WeakReference<Transaction> weakTransaction;
        /**
         * Retries left for this listener's own factory. Threaded into each replacement listener by
         * {@link Transaction#retryCurrentFactory(int, AdException)}, so the budget decreases across a chain
         * rather than resetting per attempt.
         */
        private final int retryTimeoutAttempts;

        /**
         * The factory this listener was created for, or an empty reference when it was never wired up.
         * Compared against {@link Transaction#currentCreativeFactory} so a callback from a factory the
         * transaction has already moved past or replaced is dropped instead of mutating whichever factory is
         * current now.
         */
        private WeakReference<CreativeFactory> weakFactory = new WeakReference<>(null);

        CreativeFactoryListener(Transaction transaction) {
            this(transaction, PrebidMobile.getCreativeFactoryTimeoutRetryCount());
        }

        CreativeFactoryListener(Transaction transaction, int retryTimeoutAttempts) {
            weakTransaction = new WeakReference<>(transaction);
            this.retryTimeoutAttempts = retryTimeoutAttempts;
        }

        void setFactory(CreativeFactory factory) {
            weakFactory = new WeakReference<>(factory);
        }

        /**
         * Whether this listener's factory has been superseded. An unset reference reports false, so a listener
         * that was never wired to a factory keeps working.
         */
        private boolean isSuperseded(Transaction transaction) {
            CreativeFactory factory = weakFactory.get();
            return factory != null && factory != transaction.currentCreativeFactory;
        }

        @Override
        public void onSuccess() {
            Transaction transaction = weakTransaction.get();
            if (transaction == null) {
                LogUtil.warning(TAG, "CreativeMaker is null");
                return;
            }
            if (isSuperseded(transaction)) {
                LogUtil.warning(TAG, "Ignoring success from a superseded CreativeFactory.");
                return;
            }

            // Start next CreativeFactory, if any
            if (transaction.startNextCreativeFactory()) {
                return;
            }

            // If all CreativeFactories succeeded, return success
            transaction.listener.onTransactionSuccess(transaction);
        }

        @Override
        public void onFailure(AdException e) {
            Transaction transaction = weakTransaction.get();
            if (transaction == null) {
                LogUtil.warning(TAG, "CreativeMaker is null");
                return;
            }
            if (isSuperseded(transaction)) {
                LogUtil.warning(TAG, "Ignoring failure from a superseded CreativeFactory.");
                return;
            }

            // Starting a retry is terminal for this callback: the transaction stays alive and the replacement
            // factory owns reporting the outcome.
            boolean isFactoryTimeout = CreativeFactory.isCreativeFactoryTimeout(e);
            if (isFactoryTimeout && retryTimeoutAttempts > 0) {
                transaction.retryCurrentFactory(retryTimeoutAttempts - 1, e);
                return;
            }

            transaction.listener.onTransactionFailure(e, transaction.getLoaderIdentifier());
            transaction.destroy();
        }
    }
}
