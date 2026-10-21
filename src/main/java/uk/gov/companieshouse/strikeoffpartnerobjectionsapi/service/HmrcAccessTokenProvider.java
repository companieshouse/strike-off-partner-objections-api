package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config.HmrcOAuthProperties;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.HmrcAuthenticationTokenProviderException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.HmrcOAuthTokenResponse;

/**
 * Obtains and caches OAuth access tokens used for HMRC callback requests.
 */
@Component
public class HmrcAccessTokenProvider {

    private static final String BEARER_TOKEN_TYPE = "bearer";
    private static final String TOKEN_REQUEST_FAILED = "Failed to obtain HMRC OAuth access token";
    private static final String INVALID_TOKEN_RESPONSE = "HMRC OAuth token endpoint returned an invalid response";

    private final RestTemplate restTemplate;
    private final HmrcOAuthProperties properties;
    private final Clock clock;
    private final AtomicReference<CachedAccessToken> cachedAccessToken = new AtomicReference<>();

    @Autowired
    public HmrcAccessTokenProvider(RestTemplate restTemplate, HmrcOAuthProperties properties) {
        this(restTemplate, properties, Clock.systemUTC());
    }

    HmrcAccessTokenProvider(RestTemplate restTemplate, HmrcOAuthProperties properties, Clock clock) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Returns a cached usable token or obtains a replacement from HMRC.
     *
     * @return a non-blank bearer access token
     * @throws HmrcAuthenticationTokenProviderException when token acquisition or validation fails
     */
    public String getAccessToken() {
        CachedAccessToken currentToken = cachedAccessToken.get();
        Instant now = clock.instant();
        if (isUsable(currentToken, now)) {
            return currentToken.value();
        }

        synchronized (this) {
            currentToken = cachedAccessToken.get();
            now = clock.instant();
            if (isUsable(currentToken, now)) {
                return currentToken.value();
            }

            HmrcOAuthTokenResponse response = requestAccessToken();
            validate(response);
            CachedAccessToken replacementToken = createCachedToken(response, now);
            cachedAccessToken.set(replacementToken);
            return replacementToken.value();
        }
    }

    /**
     * Invalidates the token rejected by HMRC without discarding a newer token.
     *
     * @param rejectedAccessToken token rejected by the callback endpoint
     */
    public void invalidate(String rejectedAccessToken) {
        CachedAccessToken currentToken = cachedAccessToken.get();
        if (currentToken != null && currentToken.value().equals(rejectedAccessToken)) {
            cachedAccessToken.compareAndSet(currentToken, null);
        }
    }

    private HmrcOAuthTokenResponse requestAccessToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", properties.getGrantType());
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());

        try {
            ResponseEntity<HmrcOAuthTokenResponse> response = restTemplate.postForEntity(
                    properties.getTokenUrl(), new HttpEntity<>(form, headers), HmrcOAuthTokenResponse.class);
            return response.getBody();
        } catch (HttpStatusCodeException exception) {
            throw new HmrcAuthenticationTokenProviderException(
                    TOKEN_REQUEST_FAILED + ": statusCode=" + exception.getStatusCode().value());
        } catch (RestClientException exception) {
            throw new HmrcAuthenticationTokenProviderException(TOKEN_REQUEST_FAILED);
        }
    }

    private void validate(HmrcOAuthTokenResponse response) {
        if (response == null
                || response.getAccessToken() == null
                || response.getAccessToken().isBlank()
                || response.getTokenType() == null
                || !BEARER_TOKEN_TYPE.equalsIgnoreCase(response.getTokenType())
                || response.getExpiresIn() <= 0) {
            throw new HmrcAuthenticationTokenProviderException(INVALID_TOKEN_RESPONSE);
        }
    }

    private CachedAccessToken createCachedToken(HmrcOAuthTokenResponse response, Instant obtainedAt) {
        long maximumSkew = Math.max(0, response.getExpiresIn() - 1);
        long effectiveSkew = Math.min(properties.getExpirySkewSeconds(), maximumSkew);
        Instant refreshAt = obtainedAt.plusSeconds(response.getExpiresIn() - effectiveSkew);
        return new CachedAccessToken(response.getAccessToken(), refreshAt);
    }

    private boolean isUsable(CachedAccessToken token, Instant now) {
        return token != null && now.isBefore(token.refreshAt());
    }

    private record CachedAccessToken(String value, Instant refreshAt) {
    }
}

