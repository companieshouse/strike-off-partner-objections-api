package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import uk.gov.companieshouse.api.objections.model.CallbackResourceKind;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.CallbackResult;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.HmrcCallbackPayload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@Tag("unit-test")
@ExtendWith(MockitoExtension.class)
class HmrcCallbackServiceTest {

    private static final String ENDPOINT_URL = "https://hmrc-callback.test/notify";
    private static final String COMPANY_NUMBER = "12345678";
    private static final String CORRELATION_ID = "correlation-123";
    private static final String FAILURE_REASON = "Connection timeout";

    @Mock
    private HmrcOutcomeCallbackClient callbackClient;

    private HmrcCallbackService callbackService;

    @BeforeEach
    void setUp() {
        callbackService = new HmrcCallbackService(callbackClient, ENDPOINT_URL);
    }

    @ParameterizedTest
    @MethodSource("callbackCases")
    void callback_whenSuccessful_returnsResultAndSendsCorrectPayload(
            CallbackResourceKind resourceKind,
            String resourceId,
            String resourceUri) {
        when(callbackClient.sendCallback(eq(ENDPOINT_URL), any(HmrcCallbackPayload.class)))
                .thenReturn(CORRELATION_ID);

        CallbackResult result = sendCallback(resourceKind, resourceId, resourceUri);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.correlationId()).isEqualTo(CORRELATION_ID);
        assertThat(result.failureReason()).isNull();

        ArgumentCaptor<HmrcCallbackPayload> captor =
                ArgumentCaptor.forClass(HmrcCallbackPayload.class);
        verify(callbackClient).sendCallback(eq(ENDPOINT_URL), captor.capture());

        HmrcCallbackPayload payload = captor.getValue();
        assertThat(payload.getResourceKind()).isEqualTo(resourceKind);
        assertThat(payload.getResourceId()).isEqualTo(resourceId);
        assertThat(payload.getCompanyNumber()).isEqualTo(COMPANY_NUMBER);
        assertThat(payload.getResourceUri()).isEqualTo(resourceUri);
        verifyNoMoreInteractions(callbackClient);
    }

    @ParameterizedTest
    @MethodSource("callbackCases")
    void callback_whenHttpRequestFails_returnsFailureWithoutRetry(
            CallbackResourceKind resourceKind,
            String resourceId,
            String resourceUri) {
        when(callbackClient.sendCallback(eq(ENDPOINT_URL), any(HmrcCallbackPayload.class)))
                .thenThrow(new RestClientException(FAILURE_REASON));

        CallbackResult result = sendCallback(resourceKind, resourceId, resourceUri);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.correlationId()).isNull();
        assertThat(result.failureReason()).isEqualTo(FAILURE_REASON);
        verify(callbackClient).sendCallback(eq(ENDPOINT_URL), any(HmrcCallbackPayload.class));
        verifyNoMoreInteractions(callbackClient);
    }

    @ParameterizedTest
    @MethodSource("callbackCases")
    void callback_whenExceptionMessageIsNull_usesExceptionClassName(
            CallbackResourceKind resourceKind,
            String resourceId,
            String resourceUri) {
        when(callbackClient.sendCallback(eq(ENDPOINT_URL), any(HmrcCallbackPayload.class)))
                .thenThrow(new RestClientException(null));

        CallbackResult result = sendCallback(resourceKind, resourceId, resourceUri);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.correlationId()).isNull();
        assertThat(result.failureReason()).isEqualTo("RestClientException");
        verify(callbackClient).sendCallback(eq(ENDPOINT_URL), any(HmrcCallbackPayload.class));
        verifyNoMoreInteractions(callbackClient);
    }

    private CallbackResult sendCallback(
            CallbackResourceKind resourceKind,
            String resourceId,
            String resourceUri) {
        if (resourceKind == CallbackResourceKind.STRIKE_OFF_PARTNER_OBJECTION_OBJECTION) {
            return callbackService.sendObjectionOutcomeCallback(
                    resourceId, COMPANY_NUMBER, resourceUri);
        }

        return callbackService.sendWithdrawalOutcomeCallback(
                resourceId, COMPANY_NUMBER, resourceUri);
    }

    private static Stream<Arguments> callbackCases() {
        return Stream.of(
                Arguments.of(
                        CallbackResourceKind.STRIKE_OFF_PARTNER_OBJECTION_OBJECTION,
                        "objection-123",
                        "/company/12345678/strike-off-partner-objections/objection-123"),
                Arguments.of(
                        CallbackResourceKind.STRIKE_OFF_PARTNER_OBJECTION_WITHDRAWAL,
                        "withdrawal-456",
                        "/company/12345678/strike-off-partner-objections-withdrawals/withdrawal-456")
        );
    }
}