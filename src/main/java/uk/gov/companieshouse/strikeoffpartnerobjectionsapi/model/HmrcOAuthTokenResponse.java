package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NonNull;

/**
 * Successful response returned by the HMRC OAuth token endpoint.
 */
public record HmrcOAuthTokenResponse (

    @JsonProperty("access_token")
    String accessToken,

    @JsonProperty("token_type")
    String tokenType,

    @JsonProperty("expires_in")
    long expiresIn
){

    @Override
    public @NonNull String toString() {
        return "HmrcOAuthTokenResponse{" +
                "accessToken='[REDACTED]'" +
                ", tokenType='" + tokenType + '\'' +
                ", expiresIn=" + expiresIn +
                '}';
    }
}
