package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.CallbackResourceKind;
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

        // The client does not check status code, just returns correlation ID
        String correlationId = callbackClient.sendCallback(CALLBACK_ENDPOINT_URL, payload);
        assertEquals(36, correlationId.length());
    }
}
