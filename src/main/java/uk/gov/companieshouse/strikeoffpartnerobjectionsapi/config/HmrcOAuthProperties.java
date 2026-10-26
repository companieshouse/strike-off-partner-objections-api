package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config;

import jakarta.validation.constraints.PositiveOrZero;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration required to obtain OAuth access tokens from HMRC.
 */
@Validated
@ConfigurationProperties(prefix = "hmrc.oauth")
public record HmrcOAuthProperties(

    @Nullable
    String tokenUrl,

    @Nullable
    String clientId,

    @Nullable
    String clientSecret,

    @Nullable
    String grantType,

    @PositiveOrZero
    long expirySkewSeconds
) {

    @Override
    public @NonNull String toString() {
        return "HmrcOAuthProperties[REDACTED]";
    }
}