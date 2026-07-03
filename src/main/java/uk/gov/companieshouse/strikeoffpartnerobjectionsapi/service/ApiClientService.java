package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import uk.gov.companieshouse.api.InternalApiClient;

@Component
public class ApiClientService {

    private final InternalApiClient internalApiClient;

    @Autowired
    public ApiClientService(InternalApiClient internalApiClient) {
        this.internalApiClient = internalApiClient;
    }

    public InternalApiClient getInternalApiClient() {
        return internalApiClient;
    }

}
