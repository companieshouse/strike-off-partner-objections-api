package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration required to obtain OAuth access tokens from HMRC.
 */
@Validated
@ConfigurationProperties(prefix = "hmrc.oauth")
public record HmrcOAuthProperties(

    @NotBlank
    String tokenUrl,

    @NotBlank
    String clientId,

    @NotBlank
    String clientSecret,

    @NotBlank
    String grantType,

    @PositiveOrZero
    long expirySkewSeconds
) {

    @Override
    public @NonNull String toString() {
        return "HmrcOAuthProperties[REDACTED]";
    }
}