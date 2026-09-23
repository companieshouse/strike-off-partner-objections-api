package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import java.util.UUID;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.HmrcCallbackPayload;

import static java.lang.String.format;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;

/**
 * HTTP client for sending outcome callback notifications to HMRC.
 *
 * <p>Handles serialisation of callback payloads and HTTP communication with the HMRC endpoint.
 * Connection timeouts and HTTP failures are not caught; exceptions are propagated to allow
 * the caller to implement retry logic.</p>
 */
@Component
public class HmrcOutcomeCallbackClient {

    private final RestTemplate restTemplate;

    /**
     * Constructs the client with the provided REST template.
     *
     * @param restTemplate the configured REST template for HTTP communication
     */
    public HmrcOutcomeCallbackClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Sends a callback notification to the HMRC endpoint.
     *
     * <p>Generates a correlation ID for traceability and logs the request and response.
     * Exceptions from HTTP communication or serialisation failures are propagated to
     * allow the caller to implement retry logic.</p>
     *
     * @param callbackEndpointUrl the HMRC callback endpoint URL
     * @param payload the callback payload containing resource details
     * @return a correlation ID for tracing this callback request
     * @throws RestClientException if the HTTP request fails
     */
    public String sendCallback(String callbackEndpointUrl, HmrcCallbackPayload payload) {
        String correlationId = UUID.randomUUID().toString();

        LOGGER.info(format("Sending HMRC callback: correlationId=%s, resourceId=%s, companyNumber=%s",
                correlationId, payload.getResourceId(), payload.getCompanyNumber()));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<HmrcCallbackPayload> request = new HttpEntity<>(payload, headers);

        ResponseEntity<Void> response = restTemplate.postForEntity(callbackEndpointUrl, request, Void.class);

        LOGGER.info(format("HMRC callback sent successfully: correlationId=%s, statusCode=%s, resourceId=%s",
                correlationId, response.getStatusCode(), payload.getResourceId()));

        return correlationId;
    }
}
