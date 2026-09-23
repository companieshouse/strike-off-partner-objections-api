package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

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
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.CallbackResourceKind;
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
                 2.0 // backoffMultiplier
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
                  2.0
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
}
