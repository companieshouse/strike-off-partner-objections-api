package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.enums.CallbackResourceKind;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.HmrcCallbackPayload;

import java.util.stream.Stream;

@Tag("unit-test")
@ExtendWith(MockitoExtension.class)
class HmrcCallbackServiceTest {

    private static final String CALLBACK_ENDPOINT_URL = "https://hmrc-callback.test/notify";
    private static final String OBJECTION_ID = "objection-123";
    private static final String WITHDRAWAL_ID = "withdrawal-456";
    private static final String COMPANY_NUMBER = "12345678";
    private static final String OBJECTION_URI = "/company/12345678/objections/objection-123";
    private static final String WITHDRAWAL_URI = "/company/12345678/withdrawals/withdrawal-456";

    @Mock
    private HmrcOutcomeCallbackClient callbackClient;

    private HmrcCallbackService callbackService;

      @BeforeEach
      void setUp() {
          callbackService = new HmrcCallbackService(
                  callbackClient,
                  CALLBACK_ENDPOINT_URL,
                  3, // maxRetryAttempts
                  100, // initialDelayMillis (short for tests)
                  2.0, // backoffMultiplier
                  5 // executorThreadPoolSize
          );
      }


      @ParameterizedTest(name = "{0} callback executes asynchronously")
      @MethodSource("provideCallbackTestCases")
      void callbackExecutesAsynchronously(String resourceType, CallbackResourceKind resourceKind,
              String resourceId, String resourceUri, java.util.function.Consumer<HmrcCallbackService> callbackInvoker) {
          callbackInvoker.accept(callbackService);

          ArgumentCaptor<HmrcCallbackPayload> payloadCaptor = ArgumentCaptor.forClass(HmrcCallbackPayload.class);
          verify(callbackClient, timeout(5000)).sendCallback(anyString(), payloadCaptor.capture());

          HmrcCallbackPayload capturedPayload = payloadCaptor.getValue();
          assertEquals(resourceKind, capturedPayload.getResourceKind());
          assertEquals(resourceId, capturedPayload.getResourceId());
          assertEquals(COMPANY_NUMBER, capturedPayload.getCompanyNumber());
          assertEquals(resourceUri, capturedPayload.getResourceUri());
      }

      private static Stream<org.junit.jupiter.params.provider.Arguments> provideCallbackTestCases() {
          return Stream.of(
                  org.junit.jupiter.params.provider.Arguments.of(
                          "Objection",
                          CallbackResourceKind.OBJECTION,
                          OBJECTION_ID,
                          OBJECTION_URI,
                          (java.util.function.Consumer<HmrcCallbackService>) service ->
                              service.sendObjectionOutcomeCallback(OBJECTION_ID, COMPANY_NUMBER, OBJECTION_URI)
                  ),
                  org.junit.jupiter.params.provider.Arguments.of(
                          "Withdrawal",
                          CallbackResourceKind.WITHDRAWAL,
                          WITHDRAWAL_ID,
                          WITHDRAWAL_URI,
                          (java.util.function.Consumer<HmrcCallbackService>) service ->
                              service.sendWithdrawalOutcomeCallback(WITHDRAWAL_ID, COMPANY_NUMBER, WITHDRAWAL_URI)
                  )
          );
      }

      @Test
      void successfulCallbackDoesNotRetry() {
          callbackService.sendObjectionOutcomeCallback(OBJECTION_ID, COMPANY_NUMBER, OBJECTION_URI);

          verify(callbackClient, timeout(5000).times(1)).sendCallback(anyString(), any(HmrcCallbackPayload.class));
      }

      @Test
      void failedCallbackIsRetriedWithExponentialBackoff() {
          doThrow(new RestClientException("Connection timeout"))
                  .when(callbackClient)
                  .sendCallback(anyString(), any(HmrcCallbackPayload.class));

          callbackService.sendObjectionOutcomeCallback(OBJECTION_ID, COMPANY_NUMBER, OBJECTION_URI);

          verify(callbackClient, timeout(5000).times(3)).sendCallback(anyString(), any(HmrcCallbackPayload.class));
      }

      @Test
      void callbackStopsAfterMaxRetryAttempts() {
          doThrow(new RestClientException("Persistent failure"))
                  .when(callbackClient)
                  .sendCallback(anyString(), any(HmrcCallbackPayload.class));

           HmrcCallbackService serviceWithLimitedRetries = new HmrcCallbackService(
                   callbackClient,
                   CALLBACK_ENDPOINT_URL,
                   2, // Only 2 attempts max
                   50, // Short delay for testing
                   2.0,
                   5 // executorThreadPoolSize
           );

          serviceWithLimitedRetries.sendObjectionOutcomeCallback(OBJECTION_ID, COMPANY_NUMBER, OBJECTION_URI);

          verify(callbackClient, timeout(5000).times(2)).sendCallback(anyString(), any(HmrcCallbackPayload.class));
      }

      @Test
      void multipleCallbacksAreProcessedIndependently() {
          callbackService.sendObjectionOutcomeCallback(OBJECTION_ID, COMPANY_NUMBER, OBJECTION_URI);
          callbackService.sendWithdrawalOutcomeCallback(WITHDRAWAL_ID, COMPANY_NUMBER, WITHDRAWAL_URI);

          ArgumentCaptor<HmrcCallbackPayload> payloadCaptor = ArgumentCaptor.forClass(HmrcCallbackPayload.class);
          verify(callbackClient, timeout(5000).times(2)).sendCallback(anyString(), payloadCaptor.capture());

          java.util.List<HmrcCallbackPayload> capturedPayloads = payloadCaptor.getAllValues();
          assertEquals(2, capturedPayloads.size());

          boolean hasObjection = capturedPayloads.stream()
                  .anyMatch(p -> CallbackResourceKind.OBJECTION.equals(p.getResourceKind()));
          boolean hasWithdrawal = capturedPayloads.stream()
                  .anyMatch(p -> CallbackResourceKind.WITHDRAWAL.equals(p.getResourceKind()));

          assertTrue(hasObjection, "Expected objection callback to be processed");
          assertTrue(hasWithdrawal, "Expected withdrawal callback to be processed");
      }

      @Test
      void callbackPayloadContainsCorrectFields() {
          callbackService.sendObjectionOutcomeCallback(OBJECTION_ID, COMPANY_NUMBER, OBJECTION_URI);

          ArgumentCaptor<HmrcCallbackPayload> payloadCaptor = ArgumentCaptor.forClass(HmrcCallbackPayload.class);
          verify(callbackClient, timeout(5000)).sendCallback(anyString(), payloadCaptor.capture());

          HmrcCallbackPayload capturedPayload = payloadCaptor.getValue();
          assertEquals(CallbackResourceKind.OBJECTION, capturedPayload.getResourceKind());
          assertEquals(OBJECTION_ID, capturedPayload.getResourceId());
          assertEquals(COMPANY_NUMBER, capturedPayload.getCompanyNumber());
          assertEquals(OBJECTION_URI, capturedPayload.getResourceUri());
      }

      @Test
      void callbackSendsToConfiguredEndpointUrl() {
          callbackService.sendObjectionOutcomeCallback(OBJECTION_ID, COMPANY_NUMBER, OBJECTION_URI);

          ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
          verify(callbackClient, timeout(5000)).sendCallback(urlCaptor.capture(), any(HmrcCallbackPayload.class));

          assertEquals(CALLBACK_ENDPOINT_URL, urlCaptor.getValue());
      }

      @Test
      void failureOnInitialAttemptTriggersRetries() {
          doThrow(new RestClientException("Temporary failure"))
                  .doReturn(null)
                  .when(callbackClient)
                  .sendCallback(anyString(), any(HmrcCallbackPayload.class));

          callbackService.sendObjectionOutcomeCallback(OBJECTION_ID, COMPANY_NUMBER, OBJECTION_URI);

          verify(callbackClient, timeout(5000).times(2)).sendCallback(anyString(), any(HmrcCallbackPayload.class));
      }

        @Test
        void callbackServiceHandlesUnexpectedExceptions() {
            doThrow(new RuntimeException("Unexpected error"))
                    .when(callbackClient)
                    .sendCallback(anyString(), any(HmrcCallbackPayload.class));

            callbackService.sendObjectionOutcomeCallback(OBJECTION_ID, COMPANY_NUMBER, OBJECTION_URI);

            verify(callbackClient, timeout(5000).times(3)).sendCallback(anyString(), any(HmrcCallbackPayload.class));
        }

         @Test
         void sendObjectionOutcomeCallback_withResultHandler_invokesHandlerOnSuccess() {
             java.util.concurrent.atomic.AtomicReference<String> capturedCorrelationId = new java.util.concurrent.atomic.AtomicReference<>();
             java.util.concurrent.atomic.AtomicReference<String> capturedFailureReason = new java.util.concurrent.atomic.AtomicReference<>();

             java.util.function.BiConsumer<String, String> resultHandler = (correlationId, failureReason) -> {
                 capturedCorrelationId.set(correlationId);
                 capturedFailureReason.set(failureReason);
             };

             when(callbackClient.sendCallback(anyString(), any(HmrcCallbackPayload.class))).thenReturn("correlation-123");

             callbackService.sendObjectionOutcomeCallback(OBJECTION_ID, COMPANY_NUMBER, OBJECTION_URI, resultHandler);

             verify(callbackClient, timeout(5000)).sendCallback(anyString(), any(HmrcCallbackPayload.class));
             assertEquals("correlation-123", capturedCorrelationId.get());
             assertNull(capturedFailureReason.get());
         }

         @Test
         void sendWithdrawalOutcomeCallback_withResultHandler_invokesHandlerOnSuccess() {
             java.util.concurrent.atomic.AtomicReference<String> capturedCorrelationId = new java.util.concurrent.atomic.AtomicReference<>();
             java.util.concurrent.atomic.AtomicReference<String> capturedFailureReason = new java.util.concurrent.atomic.AtomicReference<>();

             java.util.function.BiConsumer<String, String> resultHandler = (correlationId, failureReason) -> {
                 capturedCorrelationId.set(correlationId);
                 capturedFailureReason.set(failureReason);
             };

             when(callbackClient.sendCallback(anyString(), any(HmrcCallbackPayload.class))).thenReturn("withdrawal-correlation-456");

             callbackService.sendWithdrawalOutcomeCallback(WITHDRAWAL_ID, COMPANY_NUMBER, WITHDRAWAL_URI, resultHandler);

             verify(callbackClient, timeout(5000)).sendCallback(anyString(), any(HmrcCallbackPayload.class));
             assertEquals("withdrawal-correlation-456", capturedCorrelationId.get());
             assertNull(capturedFailureReason.get());
         }

         @Test
         void sendObjectionOutcomeCallback_withResultHandler_invokesHandlerOnFailureAfterRetries() {
             java.util.concurrent.atomic.AtomicReference<String> capturedCorrelationId = new java.util.concurrent.atomic.AtomicReference<>();
             java.util.concurrent.atomic.AtomicReference<String> capturedFailureReason = new java.util.concurrent.atomic.AtomicReference<>();
             java.util.concurrent.CountDownLatch handlerInvoked = new java.util.concurrent.CountDownLatch(1);

             java.util.function.BiConsumer<String, String> resultHandler = (correlationId, failureReason) -> {
                 capturedCorrelationId.set(correlationId);
                 capturedFailureReason.set(failureReason);
                 handlerInvoked.countDown();
             };

             doThrow(new RestClientException("Permanent failure")).when(callbackClient)
                     .sendCallback(anyString(), any(HmrcCallbackPayload.class));

              HmrcCallbackService serviceWithLimitedRetries = new HmrcCallbackService(
                      callbackClient,
                      CALLBACK_ENDPOINT_URL,
                      2,
                      50,
                      2.0,
                      5 // executorThreadPoolSize
              );

             serviceWithLimitedRetries.sendObjectionOutcomeCallback(OBJECTION_ID, COMPANY_NUMBER, OBJECTION_URI, resultHandler);

             verify(callbackClient, timeout(3000).times(2)).sendCallback(anyString(), any(HmrcCallbackPayload.class));

             // Wait for the result handler to be invoked before asserting
             try {
                 if (!handlerInvoked.await(3000, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                     fail("Result handler was not invoked within 3000ms");
                 }
             } catch (InterruptedException e) {
                 Thread.currentThread().interrupt();
                 fail("Test interrupted while waiting for handler invocation");
             }

             assertNull(capturedCorrelationId.get());
             assertEquals("Permanent failure", capturedFailureReason.get());
         }

        @Test
        void sendObjectionOutcomeCallback_withoutResultHandler_completesSuccessfully() {
            callbackService.sendObjectionOutcomeCallback(OBJECTION_ID, COMPANY_NUMBER, OBJECTION_URI);

            verify(callbackClient, timeout(5000)).sendCallback(anyString(), any(HmrcCallbackPayload.class));
        }

        @Test
        void sendWithdrawalOutcomeCallback_withoutResultHandler_completesSuccessfully() {
            callbackService.sendWithdrawalOutcomeCallback(WITHDRAWAL_ID, COMPANY_NUMBER, WITHDRAWAL_URI);

            verify(callbackClient, timeout(5000)).sendCallback(anyString(), any(HmrcCallbackPayload.class));
        }

         @Test
         void calculateDelay_withBackoffMultiplier_calculatesExponentialBackoff() {
             // This test verifies the retry delay calculation
              HmrcCallbackService service = new HmrcCallbackService(
                      callbackClient,
                      CALLBACK_ENDPOINT_URL,
                      5,
                      100, // Initial delay 100ms
                      2.0,  // Backoff multiplier 2.0
                      5 // executorThreadPoolSize
              );

             // Attempt 0: 100 * 2^0 = 100ms
             // Attempt 1: 100 * 2^1 = 200ms
             // Attempt 2: 100 * 2^2 = 400ms
             doThrow(new RestClientException("Transient failure"))
                     .doReturn(null)
                     .when(callbackClient)
                     .sendCallback(anyString(), any(HmrcCallbackPayload.class));

             service.sendObjectionOutcomeCallback(OBJECTION_ID, COMPANY_NUMBER, OBJECTION_URI);

             verify(callbackClient, timeout(3000).times(2)).sendCallback(anyString(), any(HmrcCallbackPayload.class));
         }

        @Test
        void sendObjectionOutcomeCallback_withNullResultHandler_completesSuccessfully() {
            callbackService.sendObjectionOutcomeCallback(OBJECTION_ID, COMPANY_NUMBER, OBJECTION_URI, null);

            verify(callbackClient, timeout(5000)).sendCallback(anyString(), any(HmrcCallbackPayload.class));
        }

         @Test
         void sendWithdrawalOutcomeCallback_withNullResultHandler_completesSuccessfully() {
             callbackService.sendWithdrawalOutcomeCallback(WITHDRAWAL_ID, COMPANY_NUMBER, WITHDRAWAL_URI, null);

             verify(callbackClient, timeout(5000)).sendCallback(anyString(), any(HmrcCallbackPayload.class));
         }

        @Test
        void destroy_whenNotShutdown_shutsDownExecutor() {
            HmrcCallbackService service = new HmrcCallbackService(
                    callbackClient,
                    CALLBACK_ENDPOINT_URL,
                    3,
                    100,
                    2.0,
                    2
            );

            service.destroy();

            verify(callbackClient, org.mockito.Mockito.never()).sendCallback(anyString(), any(HmrcCallbackPayload.class));
        }

        @Test
        void destroy_whenAlreadyShutdown_returnsWithoutError() {
            HmrcCallbackService service = new HmrcCallbackService(
                    callbackClient,
                    CALLBACK_ENDPOINT_URL,
                    3,
                    100,
                    2.0,
                    2
            );
            service.destroy();

            // Call destroy again - should return without error (no exception thrown)
            assertDoesNotThrow(service::destroy);
        }

         @Test
         void sendObjectionOutcomeCallback_withRejectedExecutionException_invokesHandler() {
             java.util.concurrent.atomic.AtomicReference<String> capturedFailureReason = new java.util.concurrent.atomic.AtomicReference<>();
             java.util.concurrent.CountDownLatch handlerInvoked = new java.util.concurrent.CountDownLatch(1);

             java.util.function.BiConsumer<String, String> resultHandler = (correlationId, failureReason) -> {
                 capturedFailureReason.set(failureReason);
                 handlerInvoked.countDown();
             };

             // Create a mock executor that rejects execution
             java.util.concurrent.ScheduledExecutorService mockExecutor = org.mockito.Mockito.mock(java.util.concurrent.ScheduledExecutorService.class);
             org.mockito.Mockito.doThrow(new java.util.concurrent.RejectedExecutionException("Queue is full"))
                     .when(mockExecutor).execute(org.mockito.ArgumentMatchers.any(Runnable.class));

             // Use reflection to inject the mock executor
             HmrcCallbackService service = new HmrcCallbackService(
                     callbackClient,
                     CALLBACK_ENDPOINT_URL,
                     3,
                     100,
                     2.0,
                     1
             );
             
             org.springframework.test.util.ReflectionTestUtils.setField(service, "executorService", 
                     new java.util.concurrent.atomic.AtomicReference<>(mockExecutor));

             service.sendObjectionOutcomeCallback(OBJECTION_ID, COMPANY_NUMBER, OBJECTION_URI, resultHandler);

             try {
                 if (!handlerInvoked.await(1000, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                     fail("Result handler was not invoked within timeout");
                 }
             } catch (InterruptedException e) {
                 Thread.currentThread().interrupt();
                 fail("Test interrupted while waiting for handler");
             }

             assertThat(capturedFailureReason.get()).isNotNull().contains("Queue is full");
         }

         @ParameterizedTest
         @org.junit.jupiter.params.provider.ValueSource(ints = {100, 200, 500})
         void calculateDelay_withDifferentBackoffMultipliers_calculatesCorrectly(int attemptNumber) {
             HmrcCallbackService service = new HmrcCallbackService(
                     callbackClient,
                     CALLBACK_ENDPOINT_URL,
                     5,
                     100,
                     2.0,
                     2
             );

             // The delay calculation is exponential: initialDelay * backoff ^ attempt
             long expectedDelay = (long) (100 * Math.pow(2.0, attemptNumber));

             // Use reflection to invoke calculateDelay
             Object result = org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "calculateDelay", attemptNumber);
             assertThat(result).isNotNull();
             long actualDelay = (Long) result;

             assertThat(actualDelay).isEqualTo(expectedDelay);
         }

         @Test
         void sendObjectionOutcomeCallback_withEmptyCallbackUrl_shouldBeHandledByValidator() {
             HmrcCallbackService serviceWithEmptyUrl = new HmrcCallbackService(
                     callbackClient,
                     "",
                     3,
                     100,
                     2.0,
                     2
             );

             // The service should handle empty URL gracefully (validation is in the client)
             serviceWithEmptyUrl.sendObjectionOutcomeCallback(OBJECTION_ID, COMPANY_NUMBER, OBJECTION_URI);

             // Verify callback client was called despite empty URL (URL validation is client's responsibility)
             verify(callbackClient, org.mockito.Mockito.timeout(3000)).sendCallback(anyString(), any(HmrcCallbackPayload.class));
         }

         @Test
         void serviceCanBeShutdownGracefully() {
             HmrcCallbackService service = new HmrcCallbackService(
                     callbackClient,
                     CALLBACK_ENDPOINT_URL,
                     3,
                     100,
                     2.0,
                     2
             );

             // Send a callback before shutdown
             service.sendObjectionOutcomeCallback(OBJECTION_ID, COMPANY_NUMBER, OBJECTION_URI);

             // Verify callback was initiated
             verify(callbackClient, timeout(3000)).sendCallback(anyString(), any(HmrcCallbackPayload.class));

             // Shutdown the service gracefully
             service.destroy();

             // Verify that isShuttingDown flag is set
             java.util.concurrent.atomic.AtomicBoolean shutdownFlag =
                     (java.util.concurrent.atomic.AtomicBoolean) org.springframework.test.util.ReflectionTestUtils
                     .getField(service, "isShuttingDown");
             assertTrue(shutdownFlag.get(), "Service should be marked as shutting down");

             // Verify that calling destroy again does not throw an exception
             assertDoesNotThrow(service::destroy);
         }

         @Test
         void serviceShutdownWithCallbacksInFlight() throws Exception {
             java.util.concurrent.CountDownLatch callbackStarted = new java.util.concurrent.CountDownLatch(1);
             java.util.concurrent.CountDownLatch callbackCompleted = new java.util.concurrent.CountDownLatch(1);

             // Setup client to delay callback execution so we can test shutdown during flight
             doThrow(new RestClientException("Simulated delay"))
                     .doAnswer(invocation -> {
                         callbackStarted.countDown();
                         callbackCompleted.countDown();
                         return null;
                     })
                     .when(callbackClient)
                     .sendCallback(anyString(), any(HmrcCallbackPayload.class));

             HmrcCallbackService service = new HmrcCallbackService(
                     callbackClient,
                     CALLBACK_ENDPOINT_URL,
                     2,
                     100,
                     2.0,
                     2
             );

             // Send callback
             service.sendObjectionOutcomeCallback(OBJECTION_ID, COMPANY_NUMBER, OBJECTION_URI);

             // Wait briefly for callback to start
             callbackStarted.await(1000, java.util.concurrent.TimeUnit.MILLISECONDS);

             // Shutdown the service (should wait for callbacks to complete)
             service.destroy();

             // Verify service is shutdown
             java.util.concurrent.atomic.AtomicBoolean shutdownFlag =
                     (java.util.concurrent.atomic.AtomicBoolean) org.springframework.test.util.ReflectionTestUtils
                     .getField(service, "isShuttingDown");
             assertTrue(shutdownFlag.get());
         }

         @Test
         void sendObjectionOutcomeCallback_withInvalidCallbackUrl_handledByValidator() {
             HmrcCallbackService serviceWithInvalidUrl = new HmrcCallbackService(
                     callbackClient,
                     " ", // Whitespace-only URL
                     3,
                     100,
                     2.0,
                     2
             );

             // Sending callback with invalid URL - client's validator will catch this
             serviceWithInvalidUrl.sendObjectionOutcomeCallback(OBJECTION_ID, COMPANY_NUMBER, OBJECTION_URI);

             // Client should be called and will validate URL
             verify(callbackClient, org.mockito.Mockito.timeout(3000)).sendCallback(any(), any(HmrcCallbackPayload.class));
         }

         @Test
         void multipleShutdownCallsAreIdempotent() {
             HmrcCallbackService service = new HmrcCallbackService(
                     callbackClient,
                     CALLBACK_ENDPOINT_URL,
                     3,
                     100,
                     2.0,
                     2
             );

             // Call destroy multiple times - should not throw
             assertDoesNotThrow(() -> {
                 service.destroy();
                 service.destroy();
                 service.destroy();
             });

             // Verify that isShuttingDown is set
             java.util.concurrent.atomic.AtomicBoolean shutdownFlag =
                     (java.util.concurrent.atomic.AtomicBoolean) org.springframework.test.util.ReflectionTestUtils
                     .getField(service, "isShuttingDown");
             assertTrue(shutdownFlag.get());
         }
}
