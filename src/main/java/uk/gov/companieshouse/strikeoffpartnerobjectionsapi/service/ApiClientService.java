package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import org.springframework.stereotype.Component;

import uk.gov.companieshouse.api.InternalApiClient;
import uk.gov.companieshouse.sdk.manager.ApiSdkManager;

@Component
public class ApiClientService {

    public InternalApiClient getInternalApiClient() {
        return ApiSdkManager.getPrivateSDK();
    }
}
