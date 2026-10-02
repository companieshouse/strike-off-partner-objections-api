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
     * <p>Validates that the callback endpoint URL is configured and the HTTP response is valid.
     * Checks that the HTTP response status is successful (2xx). Non-2xx responses or invalid
     * responses (null or missing status) are treated as failures and trigger an exception.
     * Generates a correlation ID for traceability and logs the request and response.
     * Exceptions from HTTP communication, non-2xx status codes, or serialisation failures
     * are propagated to allow the caller to implement retry logic.</p>
     *
     * Note: HMRC authentication mechanism is still being finalised. Once finalised,
     * authentication headers (e.g., ERIC headers, API key, Bearer token) must be added
     * to the HTTP request headers here.</p>
     *
     * @param callbackEndpointUrl the HMRC callback endpoint URL (must not be null or empty)
     * @param payload the callback payload containing resource details
     * @return a correlation ID for tracing this callback request
     * @throws IllegalArgumentException if callbackEndpointUrl is null or empty
     * @throws RestClientException if the HTTP request fails, returns a non-2xx status, or response is invalid
     */
    public String sendCallback(String callbackEndpointUrl, HmrcCallbackPayload payload) {
        if (callbackEndpointUrl == null || callbackEndpointUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Callback endpoint URL must not be null or empty");
        }

        String correlationId = UUID.randomUUID().toString();

        LOGGER.info(format("Sending HMRC callback: correlationId=%s, resourceId=%s, companyNumber=%s",
                correlationId, payload.getResourceId(), payload.getCompanyNumber()));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Add HMRC authentication headers once authentication mechanism is finalised

        HttpEntity<HmrcCallbackPayload> request = new HttpEntity<>(payload, headers);

        ResponseEntity<Void> response = restTemplate.postForEntity(callbackEndpointUrl, request, Void.class);

        if (response == null || response.getStatusCode() == null) {
            String errorMessage = format("HMRC callback failed: correlationId=%s, statusCode=null, resourceId=%s",
                    correlationId, payload.getResourceId());
            LOGGER.error(errorMessage);
            throw new RestClientException(errorMessage);
        }

        if (!response.getStatusCode().is2xxSuccessful()) {
            String errorMessage = format("HMRC callback failed: correlationId=%s, statusCode=%s, resourceId=%s",
                    correlationId, response.getStatusCode(), payload.getResourceId());
            LOGGER.error(errorMessage);
            throw new RestClientException(errorMessage);
        }

        LOGGER.info(format("HMRC callback sent successfully: correlationId=%s, statusCode=%s, resourceId=%s",
                correlationId, response.getStatusCode(), payload.getResourceId()));

        return correlationId;
    }
}
