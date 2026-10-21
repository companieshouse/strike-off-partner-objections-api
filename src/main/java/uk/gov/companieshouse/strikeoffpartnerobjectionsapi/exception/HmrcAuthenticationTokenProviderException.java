package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception;

import org.springframework.web.client.RestClientException;

/**
 * Indicates that a usable HMRC OAuth access token could not be obtained.
 */
public class HmrcAuthenticationTokenProviderException extends RestClientException {

    public HmrcAuthenticationTokenProviderException(String message) {
        super(message);
    }
}
