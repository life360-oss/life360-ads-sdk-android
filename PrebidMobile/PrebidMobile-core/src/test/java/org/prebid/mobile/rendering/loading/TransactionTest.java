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

import android.app.Activity;
import android.content.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.mobile.PrebidMobile;
import org.prebid.mobile.api.data.AdFormat;
import org.prebid.mobile.api.exceptions.AdException;
import org.prebid.mobile.configuration.AdUnitConfiguration;
import org.prebid.mobile.rendering.models.AbstractCreative;
import org.prebid.mobile.rendering.models.CreativeModel;
import org.prebid.mobile.rendering.models.CreativeModelsMaker;
import org.prebid.mobile.rendering.sdk.ManagersResolver;
import org.prebid.mobile.rendering.views.interstitial.InterstitialManager;
import org.prebid.mobile.test.utils.WhiteBox;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.*;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 19)
public class TransactionTest {

    private Context mockContext;

    @Before
    public void setUp() throws Exception {
        Activity testActivity = Robolectric.buildActivity(Activity.class).create().get();
        mockContext = testActivity.getApplicationContext();
        // A real CreativeFactory start() builds a WebView, which reaches ManagersResolver.
        ManagersResolver.getInstance().prepare(mockContext);
    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testTransactionInit() throws Exception {
        List<CreativeModel> creativeModels = new ArrayList<>();
        CreativeModel mockCreativeModel = mock(CreativeModel.class);
        creativeModels.add(mockCreativeModel);
        Transaction.Listener mockOxTransactionListener = mock(Transaction.Listener.class);

        // Valid
        InterstitialManager mockInterstitialManager = mock(InterstitialManager.class);
        Transaction transaction = Transaction.createTransaction(mockContext,
                createModelResult(creativeModels, "ts"),
                mockInterstitialManager,
                mockOxTransactionListener
        );
        assertNotNull(transaction);

        // No context
        boolean hasException = false;
        try {
            Transaction.createTransaction(
                null,
                createModelResult(creativeModels, "ts"),
                mockInterstitialManager,
                mockOxTransactionListener);
        }
        catch (AdException e) {
            hasException = true;
        }
        assertTrue(hasException);

        // No creative models
        hasException = false;
        try {
            Transaction.createTransaction(mockContext,
                    createModelResult(null, "ts"),
                    mockInterstitialManager,
                    mockOxTransactionListener
            );
        }
        catch (AdException e) {
            hasException = true;
        }
        assertTrue(hasException);

        // Empty creative models
        hasException = false;
        try {
            Transaction.createTransaction(mockContext,
                    createModelResult(new ArrayList<>(), "ts"),
                    mockInterstitialManager,
                    mockOxTransactionListener
            );
        }
        catch (AdException e) {
            hasException = true;
        }
        assertTrue(hasException);

        // No listener
        hasException = false;
        try {
            Transaction.createTransaction(mockContext,
                    createModelResult(creativeModels, "ts"),
                    mockInterstitialManager,
                    null
            );
        }
        catch (AdException e) {
            hasException = true;
        }
        assertTrue(hasException);
    }

    // Tests that CreativeFactory is started
    @Test
    public void testStartCreativeFactories() throws Exception {
        CreativeModel mockCreativeModel = mock(CreativeModel.class);
        List<CreativeModel> creativeModels = new ArrayList<>();
        creativeModels.add(mockCreativeModel);
        Transaction.Listener mockOxTransactionListener = mock(Transaction.Listener.class);
        final Transaction transaction = Transaction.createTransaction(
                mockContext,
                createModelResult(creativeModels, "ts"),
                mock(InterstitialManager.class),
                mockOxTransactionListener
        );

        transaction.startCreativeFactories();

        verify(mockOxTransactionListener).onTransactionFailure(any(AdException.class), anyString());
    }

    // Tests when creative factories return
    @Test
    public void testCreativeFactoryListener() throws Exception {
        List<CreativeModel> mockCreativeModels = Collections.singletonList(mock(CreativeModel.class));
        Transaction.Listener mockListener = mock(Transaction.Listener.class);
        Transaction transaction = Transaction.createTransaction(
                mockContext,
                createModelResult(mockCreativeModels, ""),
                mock(InterstitialManager.class),
                mockListener
        );
        Transaction.CreativeFactoryListener creativeFactoryListener = new Transaction.CreativeFactoryListener(
                transaction);

        // No more Creatives to construct
        // Transaction.Listener.onSuccess is called
        creativeFactoryListener.onSuccess();
        verify(mockListener).onTransactionSuccess(transaction);

        // More Creatives to construct
        // Transaction.Listener.onSuccess is not called
        reset(mockListener);
        Iterator<CreativeFactory> mockIterator = mock(Iterator.class);
        when(mockIterator.hasNext()).thenReturn(true);
        when(mockIterator.next()).thenReturn(mock(CreativeFactory.class));
        WhiteBox.setInternalState(transaction, "creativeFactoryIterator", mockIterator);
        creativeFactoryListener.onSuccess();
        verify(mockListener, never()).onTransactionSuccess(transaction);

        // On failure, Transaction.Listener.onFailure should be called
        AdException adException = new AdException("type", "message");
        creativeFactoryListener.onFailure(adException);
        verify(mockListener).onTransactionFailure(eq(adException), anyString());
    }

    @Test
    public void onSuccessWithCreativeTimeout_TransactionListenerSuccessNotCalled()
    throws Exception {
        List<CreativeModel> creativeModels = Arrays.asList(mock(CreativeModel.class), mock(CreativeModel.class));
        Transaction.Listener mockListener = mock(Transaction.Listener.class);
        InterstitialManager mockInterstitialManager = mock(InterstitialManager.class);

        Transaction transaction = Transaction.createTransaction(
                mockContext,
                createModelResult(creativeModels, ""),
                mockInterstitialManager,
                mockListener
        );
        Transaction.CreativeFactoryListener creativeFactoryListener = new Transaction.CreativeFactoryListener(
                transaction);
        Iterator<CreativeFactory> mockIterator = mock(Iterator.class);
        when(mockIterator.hasNext()).thenReturn(true);
        when(mockIterator.next()).thenReturn(mock(CreativeFactory.class));
        WhiteBox.setInternalState(transaction, "creativeFactoryIterator", mockIterator);
        creativeFactoryListener.onSuccess();
        verify(mockListener, never()).onTransactionSuccess(transaction);

        AdException adException = new AdException(AdException.INTERNAL_ERROR, "CreativeFactory Timeout");
        creativeFactoryListener.onFailure(adException);
        verify(mockListener).onTransactionFailure(eq(adException), anyString());
    }

    //region ==================== Creative-factory retry state machine

    /**
     * Starting a retry must be terminal for the callback. Falling through to
     * {@code onTransactionFailure()} + {@code destroy()} would report an ad failure to the publisher while the
     * retry is still loading.
     */
    @Test
    public void timeoutFailureWithRetriesRemaining_doesNotReportFailureAndReplacesFactoryInPlace()
    throws Exception {
        Transaction.Listener transactionListener = mock(Transaction.Listener.class);
        Transaction transaction = createTransactionWith(transactionListener);
        CreativeFactory timedOutFactory = givenCurrentFactoryWithCreative(transaction);
        List<CreativeFactory> factories = WhiteBox.getInternalState(transaction, "creativeFactories");

        AdException timeout = timeoutException();
        new Transaction.CreativeFactoryListener(transaction, 2).onFailure(timeout);

        // any(), not eq(timeout): the retry must report no failure at all while it is still loading, and a
        // matcher narrowed to the timeout would miss one raised by the replacement itself.
        verify(transactionListener, never()).onTransactionFailure(any(AdException.class), anyString());
        verify(timedOutFactory).destroy();
        // Replaced by index rather than appended, so destroy() can reach the retry and
        // TransactionManager's size/index bookkeeping is unchanged.
        assertEquals(1, factories.size());
        assertNotSame(timedOutFactory, factories.get(0));
        assertSame(factories.get(0), WhiteBox.getInternalState(transaction, "currentCreativeFactory"));
    }

    /**
     * The budget has to travel into the replacement's listener. Re-reading it from PrebidMobile per listener
     * would give each retry a full allowance again, so the chain could never terminate by attempt count.
     */
    @Test
    public void timeoutRetry_decrementsRemainingAttemptsForTheReplacementFactory() throws Exception {
        PrebidMobile.setCreativeFactoryTimeoutRetryCount(2);
        Transaction transaction = createTransactionWith(mock(Transaction.Listener.class));
        givenCurrentFactoryWithCreative(transaction);
        List<CreativeFactory> factories = WhiteBox.getInternalState(transaction, "creativeFactories");

        new Transaction.CreativeFactoryListener(transaction, 2).onFailure(timeoutException());

        Object retryListener = WhiteBox.getInternalState(factories.get(0), "listener");
        assertEquals(1, (int) WhiteBox.getInternalState(retryListener, "retryTimeoutAttempts"));
    }

    @Test
    public void timeoutFailureWithNoRetriesRemaining_reportsFailureExactlyOnce() throws Exception {
        Transaction.Listener transactionListener = mock(Transaction.Listener.class);
        Transaction transaction = createTransactionWith(transactionListener);
        givenCurrentFactoryWithCreative(transaction);

        AdException timeout = timeoutException();
        new Transaction.CreativeFactoryListener(transaction, 0).onFailure(timeout);

        verify(transactionListener, times(1)).onTransactionFailure(eq(timeout), anyString());
    }

    /**
     * A factory that times out before producing a creative is the common case, so {@code getCreative()} is
     * routinely null on the retry path.
     */
    @Test
    public void timeoutRetryWithNoCreative_reportsFailureInsteadOfThrowing() throws Exception {
        Transaction.Listener transactionListener = mock(Transaction.Listener.class);
        Transaction transaction = createTransactionWith(transactionListener);
        CreativeFactory timedOutFactory = mock(CreativeFactory.class);
        when(timedOutFactory.getCreative()).thenReturn(null);
        WhiteBox.setInternalState(transaction, "currentCreativeFactory", timedOutFactory);
        WhiteBox.setInternalState(transaction, "creativeFactories", new ArrayList<>(singletonList(timedOutFactory)));

        new Transaction.CreativeFactoryListener(transaction, 1).onFailure(timeoutException());

        verify(transactionListener, times(1)).onTransactionFailure(any(AdException.class), anyString());
    }

    private Transaction createTransactionWith(Transaction.Listener listener) throws AdException {
        return Transaction.createTransaction(
                mockContext,
                createModelResult(new ArrayList<>(singletonList(mock(CreativeModel.class))), "bid"),
                mock(InterstitialManager.class),
                listener
        );
    }

    /**
     * Installs a single mocked CreativeFactory as the current one. Its model is a loadable banner, so a
     * replacement factory's {@code start()} begins an async creative load rather than reporting a failure of
     * its own — otherwise that failure, not the retry logic, is what the assertions would observe.
     */
    private CreativeFactory givenCurrentFactoryWithCreative(Transaction transaction) throws Exception {
        AdUnitConfiguration configuration = new AdUnitConfiguration();
        configuration.setAdFormat(AdFormat.BANNER);
        CreativeModel model = mock(CreativeModel.class);
        when(model.getAdConfiguration()).thenReturn(configuration);
        when(model.getHtml()).thenReturn("<html><body>creative</body></html>");
        when(model.getWidth()).thenReturn(320);
        when(model.getHeight()).thenReturn(50);
        AbstractCreative creative = mock(AbstractCreative.class);
        when(creative.getCreativeModel()).thenReturn(model);
        CreativeFactory timedOutFactory = mock(CreativeFactory.class);
        when(timedOutFactory.getCreative()).thenReturn(creative);

        WhiteBox.setInternalState(transaction, "currentCreativeFactory", timedOutFactory);
        WhiteBox.setInternalState(transaction, "creativeFactories", new ArrayList<>(singletonList(timedOutFactory)));
        return timedOutFactory;
    }

    private AdException timeoutException() {
        return new AdException(AdException.INTERNAL_ERROR, CreativeFactory.TIMEOUT_ERROR_MESSAGE);
    }

    //endregion ==================== Creative-factory retry state machine

    private CreativeModelsMaker.Result createModelResult(List<CreativeModel> creativeModels, String state) {
        CreativeModelsMaker.Result result = new CreativeModelsMaker.Result();
        result.creativeModels = creativeModels;
        result.transactionState = state;
        result.loaderIdentifier = "123";
        return result;
    }
}