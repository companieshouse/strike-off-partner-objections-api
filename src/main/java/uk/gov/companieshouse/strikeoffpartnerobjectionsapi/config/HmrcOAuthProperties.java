package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration required to obtain OAuth access tokens from HMRC.
 */
@Validated
@ConfigurationProperties(prefix = "hmrc.oauth")
public class HmrcOAuthProperties {

    @NotBlank
    private String tokenUrl;

    @NotBlank
    private String clientId;

    @NotBlank
    private String clientSecret;

    @NotBlank
    private String grantType;

    @PositiveOrZero
    private long expirySkewSeconds;

    public HmrcOAuthProperties() {
    }

    public HmrcOAuthProperties(String tokenUrl, String clientId, String clientSecret,
                               String grantType, long expirySkewSeconds) {
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.grantType = grantType;
        this.expirySkewSeconds = expirySkewSeconds;
    }

    public String getTokenUrl() {
        return tokenUrl;
    }

    public void setTokenUrl(String tokenUrl) {
        this.tokenUrl = tokenUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getGrantType() {
        return grantType;
    }

    public void setGrantType(String grantType) {
        this.grantType = grantType;
    }

    public long getExpirySkewSeconds() {
        return expirySkewSeconds;
    }

    public void setExpirySkewSeconds(long expirySkewSeconds) {
        this.expirySkewSeconds = expirySkewSeconds;
    }
}
