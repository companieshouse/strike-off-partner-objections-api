package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config.HmrcOAuthProperties;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.HmrcAuthenticationTokenProviderException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.HmrcOAuthTokenResponse;

@Tag("unit-test")
@ExtendWith(MockitoExtension.class)
class HmrcAccessTokenProviderTest {

    private static final String TOKEN_URL = "https://hmrc.test/oauth/token";
    private static final String CLIENT_ID = "client-id-SHOULD-NOT-APPEAR";
    private static final String CLIENT_SECRET = "client-secret-SHOULD-NOT-APPEAR";
    private static final String ACCESS_TOKEN = "access-token-SHOULD-NOT-APPEAR";
    private static final String REPLACEMENT_TOKEN = "replacement-access-token";
    private static final Instant INITIAL_TIME = Instant.parse("2026-10-21T10:00:00Z");

    @Mock
    private RestTemplate restTemplate;

    private MutableClock clock;
    private HmrcOAuthProperties properties;
    private HmrcAccessTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(INITIAL_TIME);
        properties = new HmrcOAuthProperties(
                TOKEN_URL, CLIENT_ID, CLIENT_SECRET, "client_credentials", 60);
        tokenProvider = new HmrcAccessTokenProvider(restTemplate, properties, clock);
    }

    @Test
    void getAccessTokenSendsConfirmedClientCredentialsForm() {
        when(restTemplate.postForEntity(eq(TOKEN_URL), any(), eq(HmrcOAuthTokenResponse.class)))
                .thenReturn(successResponse(ACCESS_TOKEN, 3600));

        String result = tokenProvider.getAccessToken();

        assertThat(result).isEqualTo(ACCESS_TOKEN);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<MultiValueMap<String, String>>> requestCaptor =
                ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(
                eq(TOKEN_URL), requestCaptor.capture(), eq(HmrcOAuthTokenResponse.class));
        HttpEntity<MultiValueMap<String, String>> request = requestCaptor.getValue();
        assertThat(request.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_FORM_URLENCODED);
        assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isNull();
        assertThat(request.getBody()).containsEntry("grant_type", java.util.List.of("client_credentials"));
        assertThat(request.getBody()).containsEntry("client_id", java.util.List.of(CLIENT_ID));
        assertThat(request.getBody()).containsEntry("client_secret", java.util.List.of(CLIENT_SECRET));
        assertThat(request.getBody()).doesNotContainKey("scope");
    }

    @Test
    void getAccessTokenReusesTokenUntilRefreshTime() {
        when(restTemplate.postForEntity(eq(TOKEN_URL), any(), eq(HmrcOAuthTokenResponse.class)))
                .thenReturn(successResponse(ACCESS_TOKEN, 3600));

        assertThat(tokenProvider.getAccessToken()).isEqualTo(ACCESS_TOKEN);
        clock.advanceSeconds(3539);
        assertThat(tokenProvider.getAccessToken()).isEqualTo(ACCESS_TOKEN);

        verify(restTemplate).postForEntity(eq(TOKEN_URL), any(), eq(HmrcOAuthTokenResponse.class));
    }

    @Test
    void getAccessTokenReplacesTokenAtConfiguredRefreshTime() {
        when(restTemplate.postForEntity(eq(TOKEN_URL), any(), eq(HmrcOAuthTokenResponse.class)))
                .thenReturn(successResponse(ACCESS_TOKEN, 3600))
                .thenReturn(successResponse(REPLACEMENT_TOKEN, 3600));

        assertThat(tokenProvider.getAccessToken()).isEqualTo(ACCESS_TOKEN);
        clock.advanceSeconds(3540);

        assertThat(tokenProvider.getAccessToken()).isEqualTo(REPLACEMENT_TOKEN);
        verify(restTemplate, times(2)).postForEntity(
                eq(TOKEN_URL), any(), eq(HmrcOAuthTokenResponse.class));
    }

    @Test
    void invalidateRejectedTokenObtainsReplacementWithoutInvalidatingNewerToken() {
        when(restTemplate.postForEntity(eq(TOKEN_URL), any(), eq(HmrcOAuthTokenResponse.class)))
                .thenReturn(successResponse(ACCESS_TOKEN, 3600))
                .thenReturn(successResponse(REPLACEMENT_TOKEN, 3600));

        assertThat(tokenProvider.getAccessToken()).isEqualTo(ACCESS_TOKEN);
        tokenProvider.invalidate(ACCESS_TOKEN);
        assertThat(tokenProvider.getAccessToken()).isEqualTo(REPLACEMENT_TOKEN);
        tokenProvider.invalidate(ACCESS_TOKEN);

        assertThat(tokenProvider.getAccessToken()).isEqualTo(REPLACEMENT_TOKEN);
        verify(restTemplate, times(2)).postForEntity(
                eq(TOKEN_URL), any(), eq(HmrcOAuthTokenResponse.class));
    }

    @Test
    void getAccessTokenSanitizesEndpointFailure() {
        String sensitiveResponse = CLIENT_SECRET + " " + ACCESS_TOKEN;
        HttpClientErrorException endpointFailure = HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                HttpHeaders.EMPTY,
                sensitiveResponse.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
        when(restTemplate.postForEntity(eq(TOKEN_URL), any(), eq(HmrcOAuthTokenResponse.class)))
                .thenThrow(endpointFailure);

        assertThatThrownBy(tokenProvider::getAccessToken)
                .isInstanceOf(HmrcAuthenticationTokenProviderException.class)
                .hasMessage("Failed to obtain HMRC OAuth access token: statusCode=401")
                .hasMessageNotContaining(CLIENT_ID)
                .hasMessageNotContaining(CLIENT_SECRET)
                .hasMessageNotContaining(ACCESS_TOKEN)
                .hasCauseInstanceOf(RestClientException.class);
    }

    @Test
    void getAccessTokenSanitizesTransportFailure() {
        when(restTemplate.postForEntity(eq(TOKEN_URL), any(), eq(HmrcOAuthTokenResponse.class)))
                .thenThrow(new RestClientException(CLIENT_SECRET));

        assertThatThrownBy(tokenProvider::getAccessToken)
                .isInstanceOf(HmrcAuthenticationTokenProviderException.class)
                .hasMessage("Failed to obtain HMRC OAuth access token")
                .hasMessageNotContaining(CLIENT_SECRET)
                .hasCauseInstanceOf(RestClientException.class);
    }

    @Test
    void getAccessTokenRejectsMissingResponseBody() {
        when(restTemplate.postForEntity(eq(TOKEN_URL), any(), eq(HmrcOAuthTokenResponse.class)))
                .thenReturn(ResponseEntity.ok().build());

        assertThatThrownBy(tokenProvider::getAccessToken)
                .isInstanceOf(HmrcAuthenticationTokenProviderException.class)
                .hasMessage("HMRC OAuth token endpoint returned an invalid response");
    }

    @Test
    void getAccessTokenRejectsBlankToken() {
        when(restTemplate.postForEntity(eq(TOKEN_URL), any(), eq(HmrcOAuthTokenResponse.class)))
                .thenReturn(successResponse(" ", 3600));

        assertThatThrownBy(tokenProvider::getAccessToken)
                .isInstanceOf(HmrcAuthenticationTokenProviderException.class);
    }

    @Test
    void getAccessTokenRejectsUnexpectedTokenType() {
        HmrcOAuthTokenResponse response = new HmrcOAuthTokenResponse(ACCESS_TOKEN, "mac", 3600);
        when(restTemplate.postForEntity(eq(TOKEN_URL), any(), eq(HmrcOAuthTokenResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        assertThatThrownBy(tokenProvider::getAccessToken)
                .isInstanceOf(HmrcAuthenticationTokenProviderException.class);
    }

    @Test
    void getAccessTokenRejectsNonPositiveExpiry() {
        when(restTemplate.postForEntity(eq(TOKEN_URL), any(), eq(HmrcOAuthTokenResponse.class)))
                .thenReturn(successResponse(ACCESS_TOKEN, 0));

        assertThatThrownBy(tokenProvider::getAccessToken)
                .isInstanceOf(HmrcAuthenticationTokenProviderException.class);
    }

    @Test
    void sensitiveConfigurationAndResponseModelsDoNotExposeValuesThroughToString() {
        HmrcOAuthTokenResponse response = new HmrcOAuthTokenResponse(ACCESS_TOKEN, "bearer", 3600);

        assertThat(properties.toString()).doesNotContain(CLIENT_ID, CLIENT_SECRET);
        assertThat(response.toString()).doesNotContain(ACCESS_TOKEN);
    }

    private ResponseEntity<HmrcOAuthTokenResponse> successResponse(String token, long expiresIn) {
        return ResponseEntity.ok(new HmrcOAuthTokenResponse(token, "bearer", expiresIn));
    }

    private static final class MutableClock extends Clock {

        private Instant currentTime;

        private MutableClock(Instant currentTime) {
            this.currentTime = currentTime;
        }

        private void advanceSeconds(long seconds) {
            currentTime = currentTime.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return currentTime;
        }
    }
}


