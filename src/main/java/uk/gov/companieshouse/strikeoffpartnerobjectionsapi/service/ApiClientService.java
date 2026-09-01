package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import uk.gov.companieshouse.api.InternalApiClient;

/**
 * Component that provides access to the {@link InternalApiClient} for making
 * authenticated calls to internal Companies House APIs.
 */
@Component
public class ApiClientService {

    private final InternalApiClient internalApiClient;

    /**
     * Constructs the service with the pre-configured internal API client.
     *
     * @param internalApiClient the internal API client bean configured for authenticated requests
     */
    @Autowired
    public ApiClientService(InternalApiClient internalApiClient) {
        this.internalApiClient = internalApiClient;
    }

    /**
     * Returns the internal API client.
     *
     * @return the {@link InternalApiClient} instance
     */
    public InternalApiClient getInternalApiClient() {
        return internalApiClient;
    }

}
