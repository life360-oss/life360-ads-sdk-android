package org.prebid.mobile.rendering.sdk;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.prebid.mobile.api.rendering.pluginrenderer.PrebidMobilePluginRegister.PREBID_MOBILE_RENDERER_NAME;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.annotation.LooperMode.Mode.LEGACY;
import static java.lang.Thread.sleep;
import static android.os.Looper.getMainLooper;

import android.app.Activity;
import android.content.Context;


import com.life360.ads.Life360Ads;

import org.hamcrest.MatcherAssert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.prebid.mobile.PrebidMobile;
import org.prebid.mobile.api.data.InitializationStatus;
import org.prebid.mobile.api.rendering.pluginrenderer.PrebidMobilePluginRegister;
import org.prebid.mobile.reflection.Reflection;
import org.prebid.mobile.reflection.sdk.PrebidMobileReflection;
import org.prebid.mobile.rendering.listeners.SdkInitializationListener;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.LooperMode;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

@RunWith(RobolectricTestRunner.class)
@LooperMode(LEGACY)
public class SdkInitializerTest {

    private static final int TERMINATION_TIMEOUT = 10;

    private boolean calledAlready = false;
    private Boolean isSuccessful;
    private Boolean serverWarning;
    private String error;

    private MockWebServer server;
    private Context context;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        context = Robolectric.buildActivity(Activity.class).create().get();
        reset();
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
        reset();
    }

    private void reset() {
        calledAlready = false;
        isSuccessful = null;
        error = null;
        serverWarning = null;
        PrebidMobileReflection.setHost("");
        Reflection.setStaticVariableTo(PrebidMobile.class, "customStatusEndpoint", null);
        // Clears the context, the in-progress flag and the host the /status check last ran against, so a
        // test starts from an SDK that has never initialized rather than one mid-reconfiguration.
        PrebidMobileReflection.setFlagsThatSdkIsNotInitialized();
        Reflection.setStaticVariableTo(PrebidMobile.class, "disableStatusCheck", false);
        Life360Ads.setPrebidServerEnabled(true);
    }


    @Test
    public void init_putNullContextAndNullListener_initializationFail() {
        SdkInitializer.init(null, null);

        assertFalse(PrebidMobile.isSdkInitialized());
    }

    @Test
    public void init_putNullContext_initializationFail() {
        SdkInitializer.init(null, createListener());

        assertFalse(isSuccessful);
        assertFalse(PrebidMobile.isSdkInitialized());
        assertEquals(error, "Context must be not null!");
    }

    @Test
    public void init_statusResponseIsOk_initializationIsSuccessful() throws IOException, InterruptedException {
        setStatusResponse(200, "Good");

        SdkInitializer.init(context, createListener());

        advanceBackgroundTasks();

        assertTrue(isSuccessful);
        assertTrue(PrebidMobile.isSdkInitialized());
        assertNull(error);
    }

    @Test
    public void init_longStatusResponse_initializationIsFailed() throws IOException, InterruptedException {
        setStatusResponse(200, "Good");

        SdkInitializer.init(context, createListener());

        Thread.sleep(12_000);
        shadowOf(getMainLooper()).idle();

        assertFalse(isSuccessful);
        assertFalse(PrebidMobile.isSdkInitialized());
        assertEquals("Terminated by timeout.", error);
    }

    @Test
    public void init_statusResponseIsEmpty_initializationIsSuccessful() throws IOException, InterruptedException {
        setStatusResponse(204, "");

        SdkInitializer.init(context, createListener());

        advanceBackgroundTasks();

        assertTrue(isSuccessful);
        assertTrue(PrebidMobile.isSdkInitialized());
    }

    @Test
    public void init_statusResponseIsBad_statusWarning() throws InterruptedException {
        setStatusResponse(404, "");

        SdkInitializer.init(context, createListener());

        advanceBackgroundTasks();

        assertTrue(isSuccessful);
        assertTrue(PrebidMobile.isSdkInitialized());
        assertEquals("Server status is not ok! Status code: 404", error);
        assertTrue(serverWarning);
    }


    @Test
    public void init_customStatusResponseIsOk_initializationIsSuccessful() throws IOException, InterruptedException {
        setCustomStatusResponse(200, "Good");

        SdkInitializer.init(context, createListener());

        advanceBackgroundTasks();

        assertTrue(isSuccessful);
        assertTrue(PrebidMobile.isSdkInitialized());
        assertTrue(PrebidMobilePluginRegister.getInstance().containsPlugin(PREBID_MOBILE_RENDERER_NAME));
        assertNull(error);
    }

    @Test
    public void init_customStatusResponseIsEmpty_initializationIsSuccessful() throws IOException, InterruptedException {
        setCustomStatusResponse(204, "");

        SdkInitializer.init(context, createListener());

        advanceBackgroundTasks();

        assertTrue(isSuccessful);
        assertTrue(PrebidMobile.isSdkInitialized());
    }

    @Test
    public void init_customStatusResponseIsBad_statusWarning() throws InterruptedException {
        setCustomStatusResponse(404, "");

        SdkInitializer.init(context, createListener());

        advanceBackgroundTasks();

        assertTrue(isSuccessful);
        assertTrue(PrebidMobile.isSdkInitialized());
        assertEquals("Server status is not ok! Status code: 404", error);
        assertTrue(serverWarning);
    }


    //region ==================== Init on an already-initialized SDK

    @Test
    public void init_serverlessThenPrebidServerAdded_statusCheckRunsAndListenerIsCalled()
            throws InterruptedException {
        // Serverless init: no /status check, so the server that arrives next is still owed one.
        Life360Ads.setPrebidServerEnabled(false);
        SdkInitializer.init(context, null);
        advanceBackgroundTasks();
        assertTrue(PrebidMobile.isSdkInitialized());
        assertEquals(0, server.getRequestCount());

        Life360Ads.setPrebidServerEnabled(true);
        setStatusResponse(200, "Good");
        SdkInitializer.init(context, createListener());
        advanceBackgroundTasks();

        assertTrue(isSuccessful);
        assertNull(error);
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void init_serverlessThenPrebidServerWithBadStatus_warnsButStaysInitialized()
            throws InterruptedException {
        // A bad status must not tear the SDK down: the Nativo and ad-server legs still work.
        Life360Ads.setPrebidServerEnabled(false);
        SdkInitializer.init(context, null);
        advanceBackgroundTasks();

        Life360Ads.setPrebidServerEnabled(true);
        setStatusResponse(404, "");
        SdkInitializer.init(context, createListener());
        advanceBackgroundTasks();

        assertTrue(serverWarning);
        assertEquals("Server status is not ok! Status code: 404", error);
        assertTrue(PrebidMobile.isSdkInitialized());
    }

    @Test
    public void init_repeatedWithSameHost_notifiesListenerWithoutASecondStatusCheck()
            throws InterruptedException {
        setStatusResponse(200, "Good");
        SdkInitializer.init(context, null);
        advanceBackgroundTasks();
        assertEquals(1, server.getRequestCount());

        SdkInitializer.init(context, createListener());
        advanceBackgroundTasks();

        // The caller still gets an answer, but a server already reported on is not re-checked. This is what
        // keeps the lazy init in ad view constructors from firing a request per view.
        assertTrue(isSuccessful);
        assertNull(error);
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void init_alreadyInitializedWithNullListener_makesNoRequestAndDoesNotReinitialize()
            throws InterruptedException {
        setStatusResponse(200, "Good");
        SdkInitializer.init(context, null);
        advanceBackgroundTasks();

        SdkInitializer.init(context, null);
        advanceBackgroundTasks();

        assertEquals(1, server.getRequestCount());
        assertTrue(PrebidMobile.isSdkInitialized());
    }

    @Test
    public void init_whileInitializationInProgress_isIgnored() {
        Reflection.setStaticVariableTo(InitializationNotifier.class, "initializationInProgress", true);

        SdkInitializer.init(context, createListener());

        // The pending call owns the completion; a second listener would be answered out of turn.
        assertNull(isSuccessful);
        assertFalse(calledAlready);
    }

    //endregion ==================== Init on an already-initialized SDK

    @Test
    public void runBackgroundTasks_checkStartedTasks() throws InterruptedException {
        ExecutorService executorMock = mock(ExecutorService.class);
        SdkInitializer.runBackgroundTasks(mock(InitializationNotifier.class), executorMock);

        ArgumentCaptor<Callable> requesterCaptor = ArgumentCaptor.forClass(Callable.class);
        verify(executorMock, times(1)).submit(requesterCaptor.capture());
        String requesterClassName = requesterCaptor.getValue().getClass().toString();
        MatcherAssert.assertThat(requesterClassName, is(startsWith("class org.prebid.mobile.rendering.sdk.StatusRequester")));

        ArgumentCaptor<Runnable> tasksCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(executorMock, times(3)).execute(tasksCaptor.capture());

        List<Runnable> allTasks = tasksCaptor.getAllValues();
        String firstTaskName = allTasks.get(0).getClass().toString();
        MatcherAssert.assertThat(firstTaskName, is(startsWith("class org.prebid.mobile.rendering.sdk.SdkInitializer$UserConsentFetcherTask")));

        String secondTaskName = allTasks.get(1).getClass().toString();
        MatcherAssert.assertThat(secondTaskName, is(startsWith("class org.prebid.mobile.rendering.sdk.UserAgentFetcherTask")));

        verify(executorMock, times(1)).shutdown();
        verify(executorMock, times(1)).awaitTermination(TERMINATION_TIMEOUT, TimeUnit.SECONDS);
    }

    @Test
    public void runBackgroundTasks_testTermination() throws InterruptedException {
        InitializationNotifier listenerMock = mock(InitializationNotifier.class);
        ExecutorService executorMock = mock(ExecutorService.class);

        when(executorMock.awaitTermination(10, TimeUnit.SECONDS)).thenReturn(false);

        SdkInitializer.runBackgroundTasks(listenerMock, executorMock);

        verify(listenerMock, times(1)).initializationFailed("Terminated by timeout.");
    }

    @Test
    public void runBackgroundTasks_testSuccess_successStatusRequest() throws InterruptedException {
        InitializationNotifier listenerMock = mock(InitializationNotifier.class);
        ExecutorService executorMock = mock(ExecutorService.class);
        Future statusRequesterMock = mock(Future.class);

        when(executorMock.submit(any(Callable.class))).thenReturn(statusRequesterMock);
        when(executorMock.awaitTermination(TERMINATION_TIMEOUT, TimeUnit.SECONDS)).thenReturn(true);

        SdkInitializer.runBackgroundTasks(listenerMock, executorMock);

        verify(listenerMock, times(1)).initializationCompleted(null);
    }

    @Test
    public void runBackgroundTasks_testSuccess_failedStatusRequest() throws InterruptedException, ExecutionException {
        InitializationNotifier listenerMock = mock(InitializationNotifier.class);
        ExecutorService executorMock = mock(ExecutorService.class);
        Future statusRequesterMock = mock(Future.class);
        when(statusRequesterMock.get()).thenReturn("Error");

        when(executorMock.submit(any(Callable.class))).thenReturn(statusRequesterMock);
        when(executorMock.awaitTermination(TERMINATION_TIMEOUT, TimeUnit.SECONDS)).thenReturn(true);

        SdkInitializer.runBackgroundTasks(listenerMock, executorMock);

        verify(listenerMock, times(1)).initializationCompleted("Error");
    }

    @Test
    public void runBackgroundTasks_checkStartedTasks_disableStatusCheck() throws InterruptedException, ExecutionException {
        PrebidMobileReflection.setDisableStatusCheckToTrue();

        ExecutorService executorMock = mock(ExecutorService.class);
        SdkInitializer.runBackgroundTasks(mock(InitializationNotifier.class), executorMock);

        ArgumentCaptor<Callable> requesterCaptor = ArgumentCaptor.forClass(Callable.class);
        verify(executorMock, times(0)).submit(requesterCaptor.capture());
        List<Callable> capturedCallables = requesterCaptor.getAllValues();
        MatcherAssert.assertThat(capturedCallables, is(empty()));

        ArgumentCaptor<Runnable> tasksCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(executorMock, times(3)).execute(tasksCaptor.capture());

        List<Runnable> allTasks = tasksCaptor.getAllValues();
        String firstTaskName = allTasks.get(0).getClass().toString();
        MatcherAssert.assertThat(firstTaskName, is(startsWith("class org.prebid.mobile.rendering.sdk.SdkInitializer$UserConsentFetcherTask")));

        String secondTaskName = allTasks.get(1).getClass().toString();
        MatcherAssert.assertThat(secondTaskName, is(startsWith("class org.prebid.mobile.rendering.sdk.UserAgentFetcherTask")));

        verify(executorMock, times(1)).shutdown();
        verify(executorMock, times(1)).awaitTermination(TERMINATION_TIMEOUT, TimeUnit.SECONDS);
    }


    @Test
    public void runBackgroundTasks_serverlessMode_statusCheckSkipped() throws InterruptedException {
        Life360Ads.setPrebidServerEnabled(false);

        ExecutorService executorMock = mock(ExecutorService.class);
        SdkInitializer.runBackgroundTasks(mock(InitializationNotifier.class), executorMock);

        // No StatusRequester is submitted because there is no Prebid Server to check.
        ArgumentCaptor<Callable> requesterCaptor = ArgumentCaptor.forClass(Callable.class);
        verify(executorMock, times(0)).submit(requesterCaptor.capture());
        MatcherAssert.assertThat(requesterCaptor.getAllValues(), is(empty()));

        verify(executorMock, times(3)).execute(any(Runnable.class));
        verify(executorMock, times(1)).shutdown();
        verify(executorMock, times(1)).awaitTermination(TERMINATION_TIMEOUT, TimeUnit.SECONDS);
    }


    private void advanceBackgroundTasks() throws InterruptedException {
        shadowOf(getMainLooper()).idle();
        sleep(3_000);
        shadowOf(getMainLooper()).idle();
        sleep(1_000);
        shadowOf(getMainLooper()).idle();
    }


    private SdkInitializationListener createListener() {
        return status -> {
            if (calledAlready) fail();

            if (status == InitializationStatus.SUCCEEDED) {
                isSuccessful = true;
            } else if (status == InitializationStatus.SERVER_STATUS_WARNING) {
                isSuccessful = true;
                serverWarning = true;
                error = status.getDescription();
            } else {
                isSuccessful = false;
                error = status.getDescription();
            }

            calledAlready = true;
        };
    }

    private void setStatusResponse(int code, String body) {
        String host = createStatusResponse(code, body).replace("/status", "/openrtb2/auction");
        PrebidMobileReflection.setHost(host);
    }

    private void setCustomStatusResponse(int code, String body) {
        String url = createStatusResponse(code, body);
        PrebidMobileReflection.setCustomStatusEndpoint(url);
    }

    private String createStatusResponse(int code, String body) {
        MockResponse mockResponse = new MockResponse();
        mockResponse.setResponseCode(code);
        mockResponse.setBody(body);
        mockResponse.setBodyDelay(500, TimeUnit.MILLISECONDS);
        server.enqueue(mockResponse);

        try {
            server.start();
        } catch (IOException exception) {
            throw new NullPointerException(exception.getMessage());
        }


        HttpUrl url = server.url("/status");
        server.setProtocolNegotiationEnabled(false);
        return url.toString();
    }

}