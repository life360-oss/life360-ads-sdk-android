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
import android.os.Handler;
import android.os.Looper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.prebid.mobile.PrebidMobile;
import org.prebid.mobile.api.data.AdFormat;
import org.prebid.mobile.api.exceptions.AdException;
import org.prebid.mobile.configuration.AdUnitConfiguration;
import org.prebid.mobile.rendering.models.AbstractCreative;
import org.prebid.mobile.rendering.models.CreativeModel;
import org.prebid.mobile.rendering.models.CreativeModelsMaker;
import org.prebid.mobile.rendering.models.HTMLCreative;
import org.prebid.mobile.rendering.session.manager.OmAdSessionManager;
import org.prebid.mobile.rendering.video.VideoAdEvent;
import org.prebid.mobile.rendering.video.VideoCreative;
import org.prebid.mobile.rendering.video.VideoCreativeModel;
import org.prebid.mobile.rendering.views.interstitial.InterstitialManager;
import org.prebid.mobile.test.utils.WhiteBox;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.prebid.mobile.rendering.models.CreativeModelsMakerVast.HTML_CREATIVE_TAG;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 19)
public class CreativeFactoryTest {

    private Context mockContext;
    private CreativeModel mockModel;
    private CreativeFactory.Listener mockListener;
    private OmAdSessionManager mockOmAdSessionManager;
    private InterstitialManager mockInterstitialManager;

    @Before
    public void setUp() throws Exception {
        mockContext = Robolectric.buildActivity(Activity.class).create().get();
        mockModel = mock(CreativeModel.class);
        mockListener = mock(CreativeFactory.Listener.class);
        mockOmAdSessionManager = mock(OmAdSessionManager.class);
        mockInterstitialManager = mock(InterstitialManager.class);
    }

    @Test
    public void testCreativeFactory() throws Exception {
        boolean hasException;

        // Valid
        new CreativeFactory(mockContext, mockModel, mockListener, mockOmAdSessionManager, mockInterstitialManager);

        // Null context
        hasException = false;
        try {
            new CreativeFactory(null, mockModel, mockListener, mockOmAdSessionManager, mockInterstitialManager);
        }
        catch (AdException e) {
            hasException = true;
        }
        assertTrue(hasException);

        // Null creative model
        hasException = false;
        try {
            new CreativeFactory(mockContext, null, mockListener, mockOmAdSessionManager, mockInterstitialManager);
        }
        catch (AdException e) {
            hasException = true;
        }
        assertTrue(hasException);

        // Null listener
        hasException = false;
        try {
            new CreativeFactory(mockContext, mockModel, null, mockOmAdSessionManager, mockInterstitialManager);
        }
        catch (AdException e) {
            hasException = true;
        }
        assertTrue(hasException);
    }

    @Test
    public void testAttemptAuidCreative() throws Exception {
        AdUnitConfiguration adConfiguration = new AdUnitConfiguration();
        adConfiguration.setAdFormat(AdFormat.BANNER);
        Handler mockHandler = mock(Handler.class);
        when(mockModel.getAdConfiguration()).thenReturn(adConfiguration);
        when(mockModel.getName()).thenReturn(HTML_CREATIVE_TAG);
        when(mockModel.getImpressionUrl()).thenReturn("impressionUrl");
        when(mockModel.getClickUrl()).thenReturn("clickUrl");
        PrebidMobile.setCreativeFactoryTimeout(7000);
        //Run the creativeFactory
        CreativeFactory creativeFactory = new CreativeFactory(mockContext,
                mockModel,
                mockListener,
                mockOmAdSessionManager,
                mockInterstitialManager
        );
        WhiteBox.field(CreativeFactory.class, "timeoutHandler").set(creativeFactory, mockHandler);
        creativeFactory.start();

        AbstractCreative creative = creativeFactory.getCreative();
        assertNotNull(creative);
        assertTrue(creative instanceof HTMLCreative);
        verify(mockHandler).postDelayed(any(Runnable.class), eq(7_000L));
    }

    @Test
    public void testAttemptVastCreative() throws Exception {
        VideoCreativeModel mockVideoModel = mock(VideoCreativeModel.class);
        AdUnitConfiguration adConfiguration = new AdUnitConfiguration();
        Handler mockHandler = mock(Handler.class);
        adConfiguration.setAdFormat(AdFormat.VAST);
        PrebidMobile.setCreativeFactoryTimeoutPreRenderContent(25000);
        HashMap<VideoAdEvent.Event, ArrayList<String>> videoEventsUrls = new HashMap<>();
        videoEventsUrls.put(VideoAdEvent.Event.AD_EXPAND, new ArrayList<>(Arrays.asList("AD_EXPAND")));
        when(mockVideoModel.getVideoEventUrls()).thenReturn(videoEventsUrls);
        when(mockVideoModel.getAdConfiguration()).thenReturn(adConfiguration);
        CreativeFactory creativeFactory;

        // Blank media URL
        when(mockVideoModel.getMediaUrl()).thenReturn("");
        creativeFactory = new CreativeFactory(mockContext,
                mockVideoModel,
                mockListener,
                mockOmAdSessionManager,
                mockInterstitialManager
        );
        creativeFactory.start();
        assertNull(WhiteBox.getInternalState(creativeFactory, "creative"));

        // Valid
        when(mockVideoModel.getMediaUrl()).thenReturn("mediaUrl");
        creativeFactory = new CreativeFactory(mockContext,
                mockVideoModel,
                mockListener,
                mockOmAdSessionManager,
                mockInterstitialManager
        );
        WhiteBox.field(CreativeFactory.class, "timeoutHandler").set(creativeFactory, mockHandler);
        creativeFactory.start();

        AbstractCreative creative = creativeFactory.getCreative();
        assertNotNull(creative);
        assertTrue(creative instanceof VideoCreative);
        verify(mockHandler).postDelayed(any(Runnable.class), eq(25_000L));
    }

    @Test
    public void testCreativeFactoryCreativeResolutionListener() throws Exception {
        CreativeFactory mockCreativeFactory = mock(CreativeFactory.class);
        CreativeFactory.Listener mockCreativeFactoryListener = mock(CreativeFactory.Listener.class);
        CreativeFactory.CreativeFactoryCreativeResolutionListener creativeResolutionListener = new CreativeFactory.CreativeFactoryCreativeResolutionListener(
                mockCreativeFactory);

        WhiteBox.field(CreativeFactory.class, "listener").set(mockCreativeFactory, mockCreativeFactoryListener);
        WhiteBox.field(CreativeFactory.class, "timeoutHandler").set(mockCreativeFactory, mock(Handler.class));

        // Success
        creativeResolutionListener.creativeReady(mock(AbstractCreative.class));
        verify(mockCreativeFactoryListener).onSuccess();

        // Failure
        AdException adException = new AdException(AdException.INTERNAL_ERROR, "msg");
        creativeResolutionListener.creativeFailed(adException);

        verify(mockCreativeFactoryListener).onFailure(adException);
    }

    @Test
    public void creativeReadyWithExpiredTimeoutStatus_FactoryListenerNotCalled()
    throws IllegalAccessException {
        CreativeFactory mockCreativeFactory = mock(CreativeFactory.class);
        CreativeFactory.Listener mockCreativeFactoryListener = mock(CreativeFactory.Listener.class);
        CreativeFactory.TimeoutState expired = CreativeFactory.TimeoutState.EXPIRED;
        CreativeFactory.CreativeFactoryCreativeResolutionListener creativeResolutionListener = new CreativeFactory.CreativeFactoryCreativeResolutionListener(
                mockCreativeFactory);

        WhiteBox.field(CreativeFactory.class, "listener").set(mockCreativeFactory, mockCreativeFactoryListener);
        WhiteBox.field(CreativeFactory.class, "timeoutState").set(mockCreativeFactory, expired);

        creativeResolutionListener.creativeReady(mock(AbstractCreative.class));

        verify(mockCreativeFactoryListener, never()).onSuccess();
    }

    /**
     * Asserts the effect against a real Handler rather than verifying a {@code removeCallbacks} call on a mock.
     * A mocked Handler accepts any argument, so it cannot tell a cancellation that works from one that does
     * not.
     */
    @Test
    public void destroyBeforeTimeout_timeoutCallbackDoesNotFire() throws Exception {
        CreativeFactory creativeFactory = new CreativeFactory(mockContext,
                mockModel,
                mockListener,
                mockOmAdSessionManager,
                mockInterstitialManager
        );
        WhiteBox.method(CreativeFactory.class, "markWorkStart", long.class).invoke(creativeFactory, 50L);
        assertTrue(creativeFactory.hasPendingTimeout());

        creativeFactory.destroy();

        assertFalse(creativeFactory.hasPendingTimeout());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
        verify(mockListener, never()).onFailure(any(AdException.class));
    }

    /** Control for {@link #destroyBeforeTimeout_timeoutCallbackDoesNotFire()}: the timeout still works. */
    @Test
    public void timeoutExpiresWithoutDestroy_reportsTimeoutFailure() throws Exception {
        CreativeFactory creativeFactory = new CreativeFactory(mockContext,
                mockModel,
                mockListener,
                mockOmAdSessionManager,
                mockInterstitialManager
        );
        WhiteBox.method(CreativeFactory.class, "markWorkStart", long.class).invoke(creativeFactory, 50L);

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));

        ArgumentCaptor<AdException> captor = ArgumentCaptor.forClass(AdException.class);
        verify(mockListener).onFailure(captor.capture());
        assertTrue(CreativeFactory.isCreativeFactoryTimeout(captor.getValue()));
        assertFalse(creativeFactory.hasPendingTimeout());
    }

    /**
     * AdException.getMessage() prefixes the error type, so comparing it against TIMEOUT_ERROR_MESSAGE directly
     * silently never matches. This pins the helper against the exception the factory actually throws.
     */
    @Test
    public void isCreativeFactoryTimeout_matchesTheExceptionTheFactoryActuallyThrows() {
        AdException timeout = new AdException(AdException.INTERNAL_ERROR, CreativeFactory.TIMEOUT_ERROR_MESSAGE);

        assertTrue(CreativeFactory.isCreativeFactoryTimeout(timeout));
        assertFalse(CreativeFactory.isCreativeFactoryTimeout(null));
        assertFalse(CreativeFactory.isCreativeFactoryTimeout(
                new AdException(AdException.INTERNAL_ERROR, "Some other error")));
    }

    /** A second markWorkStart must not leave the first timeout armed. */
    @Test
    public void markWorkStartTwice_onlyTheLatestTimeoutIsArmed() throws Exception {
        CreativeFactory creativeFactory = new CreativeFactory(mockContext,
                mockModel,
                mockListener,
                mockOmAdSessionManager,
                mockInterstitialManager
        );
        WhiteBox.method(CreativeFactory.class, "markWorkStart", long.class).invoke(creativeFactory, 50L);
        WhiteBox.method(CreativeFactory.class, "markWorkStart", long.class).invoke(creativeFactory, 500L);

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
        verify(mockListener, never()).onFailure(any(AdException.class));

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
        verify(mockListener, times(1)).onFailure(any(AdException.class));
    }

    @Test
    public void timeoutOccurs_CreativeFactoryListenerInitializedWithCorrectRetryCount()
    throws Exception {
        // Set retry count to 3
        PrebidMobile.setCreativeFactoryTimeoutRetryCount(3);
        
        // Create a mock transaction
        Transaction mockTransaction = mock(Transaction.class);
        
        // Create CreativeFactoryListener - it should get retry count from PrebidMobile
        Transaction.CreativeFactoryListener factoryListener = new Transaction.CreativeFactoryListener(mockTransaction);
        
        // Verify initial retry count is set from PrebidMobile configuration
        int retryCount = WhiteBox.getInternalState(factoryListener, "retryTimeoutAttempts");
        assertEquals(3, retryCount);
        
        // Test with different retry count
        PrebidMobile.setCreativeFactoryTimeoutRetryCount(5);
        Transaction.CreativeFactoryListener factoryListener2 = new Transaction.CreativeFactoryListener(mockTransaction);
        int retryCount2 = WhiteBox.getInternalState(factoryListener2, "retryTimeoutAttempts");
        assertEquals(5, retryCount2);
    }

    @Test
    public void nonTimeoutFailure_CreativeFactoryListenerDoesNotRetry()
    throws Exception {
        // Set retry count to 2
        PrebidMobile.setCreativeFactoryTimeoutRetryCount(2);
        
        // Create real transaction with mocked dependencies
        List<CreativeModel> creativeModels = new ArrayList<>();
        creativeModels.add(mockModel);
        Transaction.Listener mockTransactionListener = mock(Transaction.Listener.class);
        
        Transaction transaction = Transaction.createTransaction(
                mockContext,
                createModelResult(creativeModels, "test"),
                mockInterstitialManager,
                mockTransactionListener
        );
        
        // Create CreativeFactoryListener
        Transaction.CreativeFactoryListener factoryListener = new Transaction.CreativeFactoryListener(transaction);
        
        // Get initial retry count
        int initialRetryCount = WhiteBox.getInternalState(factoryListener, "retryTimeoutAttempts");
        assertEquals(2, initialRetryCount);
        
        // Create a non-timeout exception
        AdException nonTimeoutException = new AdException(AdException.INTERNAL_ERROR, "Some other error");
        
        // Non-timeout failure - should NOT trigger retry, retry count should remain unchanged
        factoryListener.onFailure(nonTimeoutException);
        int retryCountAfterFailure = WhiteBox.getInternalState(factoryListener, "retryTimeoutAttempts");
        assertEquals(2, retryCountAfterFailure); // Should remain unchanged
        
        // Verify transaction failure was called
        verify(mockTransactionListener, times(1)).onTransactionFailure(eq(nonTimeoutException), anyString());
    }

    private CreativeModelsMaker.Result createModelResult(List<CreativeModel> creativeModels, String state) {
        CreativeModelsMaker.Result result = new CreativeModelsMaker.Result();
        result.creativeModels = creativeModels;
        result.transactionState = state;
        result.loaderIdentifier = "123";
        return result;
    }
}
