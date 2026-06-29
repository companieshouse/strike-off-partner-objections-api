package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import uk.gov.companieshouse.api.InternalApiClient;
import uk.gov.companieshouse.api.handler.company.CompanyResourceHandler;
import uk.gov.companieshouse.api.handler.company.request.CompanyGet;
import uk.gov.companieshouse.api.model.ApiResponse;
import uk.gov.companieshouse.api.model.company.CompanyProfileApi;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@TestConfiguration
public class TestApiClientConfiguration {

    @Bean
    @Primary
    InternalApiClient internalApiClient() throws Exception {
        InternalApiClient mockClient = Mockito.mock(InternalApiClient.class);
        CompanyResourceHandler companyHandler = Mockito.mock(CompanyResourceHandler.class);
        CompanyGet companyGet = Mockito.mock(CompanyGet.class);
        ApiResponse<CompanyProfileApi> apiResponse = Mockito.mock(ApiResponse.class);
        CompanyProfileApi companyProfile = new CompanyProfileApi();
        companyProfile.setCompanyName("Test Company");

        when(mockClient.company()).thenReturn(companyHandler);
        when(companyHandler.get(anyString())).thenReturn(companyGet);
        when(companyGet.execute()).thenReturn(apiResponse);
        when(apiResponse.getData()).thenReturn(companyProfile);

        return mockClient;
    }
}
