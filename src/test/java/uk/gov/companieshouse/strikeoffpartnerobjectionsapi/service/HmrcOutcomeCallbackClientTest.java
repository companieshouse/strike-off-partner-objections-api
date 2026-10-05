package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.enums.CallbackResourceKind;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.HmrcCallbackPayload;

@Tag("unit-test")
@ExtendWith(MockitoExtension.class)
class HmrcOutcomeCallbackClientTest {

    private static final String CALLBACK_ENDPOINT_URL = "https://hmrc-callback.test/notify";
    private static final String OBJECTION_ID = "objection-123";
    private static final String COMPANY_NUMBER = "12345678";
    private static final String OBJECTION_URI = "/company/12345678/objections/objection-123";

    @Mock
    private RestTemplate restTemplate;

    private HmrcOutcomeCallbackClient callbackClient;

    @BeforeEach
    void setUp() {
        callbackClient = new HmrcOutcomeCallbackClient(restTemplate);
    }

    @Test
    void sendCallbackSendsPayloadSuccessfully() {
        HmrcCallbackPayload payload = new HmrcCallbackPayload(
                CallbackResourceKind.OBJECTION,
                OBJECTION_ID,
                COMPANY_NUMBER,
                OBJECTION_URI
        );

        ResponseEntity<Void> successResponse = ResponseEntity.ok().build();
        when(restTemplate.postForEntity(eq(CALLBACK_ENDPOINT_URL),
                org.mockito.ArgumentMatchers.any(), eq(Void.class)))
                .thenReturn(successResponse);

        String correlationId = callbackClient.sendCallback(CALLBACK_ENDPOINT_URL, payload);

        assertEquals(36, correlationId.length()); // UUID length

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<HmrcCallbackPayload>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq(CALLBACK_ENDPOINT_URL), entityCaptor.capture(), eq(Void.class));

        HttpEntity<HmrcCallbackPayload> capturedEntity = entityCaptor.getValue();
        assertEquals(payload, capturedEntity.getBody());
    }

    @Test
    void sendCallbackReturnsValidUUID() {
        HmrcCallbackPayload payload = new HmrcCallbackPayload(
                CallbackResourceKind.WITHDRAWAL,
                "withdrawal-456",
                COMPANY_NUMBER,
                "/company/12345678/withdrawals/withdrawal-456"
        );

        ResponseEntity<Void> successResponse = ResponseEntity.ok().build();
        when(restTemplate.postForEntity(anyString(), org.mockito.ArgumentMatchers.any(), eq(Void.class)))
                .thenReturn(successResponse);

        String correlationId = callbackClient.sendCallback(CALLBACK_ENDPOINT_URL, payload);

        // Verify it's a valid UUID format (contains dashes at expected positions)
        assertEquals(36, correlationId.length());
        assertEquals('-', correlationId.charAt(8));
        assertEquals('-', correlationId.charAt(13));
        assertEquals('-', correlationId.charAt(18));
        assertEquals('-', correlationId.charAt(23));
    }

    @Test
    void sendCallbackThrowsExceptionOnRestClientFailure() {
        HmrcCallbackPayload payload = new HmrcCallbackPayload(
                CallbackResourceKind.OBJECTION,
                OBJECTION_ID,
                COMPANY_NUMBER,
                OBJECTION_URI
        );

        RestClientException restException = new RestClientException("Connection timeout");
        when(restTemplate.postForEntity(anyString(), org.mockito.ArgumentMatchers.any(), eq(Void.class)))
                .thenThrow(restException);

        assertThrows(RestClientException.class, () ->
                callbackClient.sendCallback(CALLBACK_ENDPOINT_URL, payload));
    }

    @Test
    void sendCallbackHandlesNon200StatusCodes() {
        HmrcCallbackPayload payload = new HmrcCallbackPayload(
                CallbackResourceKind.OBJECTION,
                OBJECTION_ID,
                COMPANY_NUMBER,
                OBJECTION_URI
        );

        ResponseEntity<Void> errorResponse = ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        when(restTemplate.postForEntity(anyString(), org.mockito.ArgumentMatchers.any(), eq(Void.class)))
                .thenReturn(errorResponse);

        // The client should throw RestClientException on non-2xx status codes
        assertThrows(RestClientException.class, () -> callbackClient.sendCallback(CALLBACK_ENDPOINT_URL, payload));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {400, 401, 403, 404, 405, 500, 502, 503})
    void sendCallback_withVariousErrorStatusCodes_throwsRestClientException(int statusCode) {
        HmrcCallbackPayload payload = new HmrcCallbackPayload(
                uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.enums.CallbackResourceKind.OBJECTION,
                "objection-123",
                "12345678",
                "/company/12345678/strike-off/objections/objection-123"
        );

        org.springframework.http.ResponseEntity<Void> errorResponse = new org.springframework.http.ResponseEntity<>(
                org.springframework.http.HttpStatus.valueOf(statusCode)
        );

        when(restTemplate.postForEntity(anyString(), org.mockito.ArgumentMatchers.any(), eq(Void.class)))
                .thenReturn(errorResponse);

        assertThrows(RestClientException.class, () -> callbackClient.sendCallback(CALLBACK_ENDPOINT_URL, payload));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void sendCallback_withInvalidUrl_throwsIllegalArgumentException(String invalidUrl) {
        HmrcCallbackPayload payload = new HmrcCallbackPayload(
                uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.enums.CallbackResourceKind.OBJECTION,
                "objection-123",
                "12345678",
                "/company/12345678/strike-off/objections/objection-123"
        );

        assertThrows(IllegalArgumentException.class, () -> callbackClient.sendCallback(invalidUrl, payload));
    }

    @Test
    void sendCallback_withNullUrl_throwsIllegalArgumentException() {
        HmrcCallbackPayload payload = new HmrcCallbackPayload(
                uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.enums.CallbackResourceKind.OBJECTION,
                "objection-123",
                "12345678",
                "/company/12345678/strike-off/objections/objection-123"
        );

        assertThrows(IllegalArgumentException.class, () -> callbackClient.sendCallback(null, payload));
    }

    @Test
    void sendCallback_generatesUniqueCorrelationIds() {
        HmrcCallbackPayload payload = new HmrcCallbackPayload(
                uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.enums.CallbackResourceKind.WITHDRAWAL,
                "withdrawal-456",
                "87654321",
                "/company/87654321/strike-off/withdrawals/withdrawal-456"
        );

        org.springframework.http.ResponseEntity<Void> successResponse = new org.springframework.http.ResponseEntity<>(
                org.springframework.http.HttpStatus.OK
        );

        when(restTemplate.postForEntity(anyString(), org.mockito.ArgumentMatchers.any(), eq(Void.class)))
                .thenReturn(successResponse);

        String correlationId1 = callbackClient.sendCallback(CALLBACK_ENDPOINT_URL, payload);
        String correlationId2 = callbackClient.sendCallback(CALLBACK_ENDPOINT_URL, payload);

        assertThat(correlationId1)
                .isNotNull()
                .isNotEqualTo(correlationId2);
        assertThat(correlationId2)
                .isNotNull();
    }
}
