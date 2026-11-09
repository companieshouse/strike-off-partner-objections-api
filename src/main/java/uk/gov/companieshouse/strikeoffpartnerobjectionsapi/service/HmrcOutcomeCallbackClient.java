package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import uk.gov.companieshouse.logging.util.DataMap;
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
    private final HmrcAccessTokenProvider accessTokenProvider;

    /**
     * Constructs the client with the provided REST template.
     *
     * @param restTemplate the configured REST template for HTTP communication
     * @param accessTokenProvider provider of cached HMRC OAuth access tokens
     */
    public HmrcOutcomeCallbackClient(RestTemplate restTemplate, HmrcAccessTokenProvider accessTokenProvider) {
        this.restTemplate = restTemplate;
        this.accessTokenProvider = accessTokenProvider;
    }

    /**
     * Sends a callback notification to the HMRC endpoint.
     *
     * <p>Validates that the callback endpoint URL is configured and the HTTP response status
     * is successful (2xx). Non-2xx responses are treated as failures and trigger an exception.
     * Generates a correlation ID for traceability and authenticates the request with an OAuth
     * bearer token. A token rejected with HTTP 401 is replaced and the request is repeated once.
     * Exceptions from HTTP communication, non-2xx status codes, or serialisation failures
     * are propagated to allow the caller to implement retry logic.</p>
     *
     * @param callbackEndpointUrl the HMRC callback endpoint URL (must not be null or empty)
     * @param payload the callback payload containing resource details
     * @return a correlation ID for tracing this callback request
     * @throws IllegalArgumentException if callbackEndpointUrl is null or empty
     * @throws RestClientException if the HTTP request fails or returns a non-2xx status
     */
    public String sendCallback(String callbackEndpointUrl, HmrcCallbackPayload payload) {
        String correlationId = UUID.randomUUID().toString();

        var logMap = new DataMap.Builder()
                .correlationId(correlationId)
                .resourceId(payload.getResourceId())
                .companyNumber(payload.getCompanyNumber())
                .build()
                .getLogMap();
        LOGGER.info("Attempting to send HMRC callback", logMap);
        if (callbackEndpointUrl == null || callbackEndpointUrl.trim().isEmpty()) {
            LOGGER.error("Callback endpoint URL is null or empty", logMap);
            throw new IllegalArgumentException("Callback endpoint URL must not be null or empty");
        }

        LOGGER.info(format("Callback endpoint configured; retrieving HMRC access token: correlationId=%s, resourceId=%s, companyNumber=%s",
                correlationId, payload.getResourceId(), payload.getCompanyNumber()), logMap);

        String accessToken = accessTokenProvider.getAccessToken();
        try {
            ResponseEntity<Void> response = sendAuthenticatedCallback(
                    callbackEndpointUrl, payload, correlationId, accessToken);
            if (response.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                LOGGER.info(format("HMRC callback returned 401 Unauthorized, attempting token replacement: correlationId=%s, resourceId=%s",
                        correlationId, payload.getResourceId()), logMap);
                response = replaceTokenAndRetry(callbackEndpointUrl, payload, correlationId, accessToken);
            }

            validateSuccessfulResponse(response, correlationId, payload.getResourceId());
        } catch (HttpClientErrorException.Unauthorized exception) {
            LOGGER.info(format("HMRC callback returned 401 Unauthorized, attempting token replacement: correlationId=%s, resourceId=%s",
                    correlationId, payload.getResourceId()), logMap);
            ResponseEntity<Void> response = replaceTokenAndRetry(
                    callbackEndpointUrl, payload, correlationId, accessToken);

            validateSuccessfulResponse(response, correlationId, payload.getResourceId());
        }

        LOGGER.info(format("HMRC callback sent successfully: correlationId=%s, resourceId=%s",
                correlationId, payload.getResourceId()), logMap);

        return correlationId;
    }

    private ResponseEntity<Void> replaceTokenAndRetry(String callbackEndpointUrl, HmrcCallbackPayload payload,
                                                       String correlationId, String rejectedAccessToken) {
        accessTokenProvider.invalidate(rejectedAccessToken);
        String replacementAccessToken = accessTokenProvider.getAccessToken();
        return sendAuthenticatedCallback(callbackEndpointUrl, payload, correlationId, replacementAccessToken);
    }

    private ResponseEntity<Void> sendAuthenticatedCallback(String callbackEndpointUrl, HmrcCallbackPayload payload,
                                                            String correlationId, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        headers.set("X-Correlation-ID", correlationId);
        headers.setDate(Instant.now().toEpochMilli());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<HmrcCallbackPayload> request = new HttpEntity<>(payload, headers);

        LOGGER.debug(format("HMRC callback request prepared: correlationId=%s, resourceId=%s, contentType=%s",
                correlationId, payload.getResourceId(), MediaType.APPLICATION_JSON));

        return restTemplate.postForEntity(callbackEndpointUrl, request, Void.class);
    }

    private void validateSuccessfulResponse(ResponseEntity<Void> response, String correlationId, String resourceId) {
        if (!response.getStatusCode().is2xxSuccessful()) {
            String errorMessage = format("HMRC callback failed: correlationId=%s, statusCode=%s, resourceId=%s",
                    correlationId, response.getStatusCode(), resourceId);
            LOGGER.error(errorMessage);
            throw new RestClientException(errorMessage);
        }
    }
}
