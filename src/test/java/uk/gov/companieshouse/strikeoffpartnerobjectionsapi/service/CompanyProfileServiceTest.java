package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.companieshouse.api.InternalApiClient;
import uk.gov.companieshouse.api.error.ApiErrorResponseException;
import uk.gov.companieshouse.api.handler.company.CompanyResourceHandler;
import uk.gov.companieshouse.api.handler.company.request.CompanyGet;
import uk.gov.companieshouse.api.handler.exception.URIValidationException;
import uk.gov.companieshouse.api.model.ApiResponse;
import uk.gov.companieshouse.api.model.company.CompanyProfileApi;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.ServiceException;

@Tag("unit-test")
@ExtendWith(MockitoExtension.class)
class CompanyProfileServiceTest {

    private static final String COMPANY_NUMBER = "12345678";

    @Mock
    private ApiClientService apiClientService;

    @Mock
    private InternalApiClient internalApiClient;

    @Mock
    private CompanyResourceHandler companyResourceHandler;

    @Mock
    private CompanyGet companyGet;

    @Mock
    private ApiResponse<CompanyProfileApi> apiResponse;

    private CompanyProfileService companyProfileService;

    @BeforeEach
    void setUp() {
        companyProfileService = new CompanyProfileService(apiClientService);
    }

    @Test
    void getCompanyProfileReturnsDataWhenRequestSucceeds() throws Exception {
        CompanyProfileApi companyProfile = new CompanyProfileApi();
        companyProfile.setCompanyName("Test Company");
        stubApiChain();
        when(companyGet.execute()).thenReturn(apiResponse);
        when(apiResponse.getData()).thenReturn(companyProfile);

        CompanyProfileApi result = companyProfileService.getCompanyProfile(COMPANY_NUMBER);

        assertSame(companyProfile, result);
    }

    @Test
    void getCompanyProfileReturnsNullWhenApiReturnsNoData() throws Exception {
        stubApiChain();
        when(companyGet.execute()).thenReturn(apiResponse);
        when(apiResponse.getData()).thenReturn(null);

        CompanyProfileApi result = companyProfileService.getCompanyProfile(COMPANY_NUMBER);

        assertNull(result);
    }

    @Test
    void getCompanyProfileReturnsNullWhenCompanyNotFound() throws Exception {
        stubApiChain();
        when(companyGet.execute()).thenThrow(apiErrorResponseException(404, "Not Found"));

        CompanyProfileApi result = companyProfileService.getCompanyProfile(COMPANY_NUMBER);

        assertNull(result);
    }

    @Test
    void getCompanyProfileWrapsApiErrorResponseException() throws Exception {
        ApiErrorResponseException apiError = apiErrorResponseException(502, "Bad Gateway");
        stubApiChain();
        when(companyGet.execute()).thenThrow(apiError);

        ServiceException thrown = assertThrows(
                ServiceException.class,
                () -> companyProfileService.getCompanyProfile(COMPANY_NUMBER));

        assertEquals("Error retrieving company profile", thrown.getMessage());
        assertSame(apiError, thrown.getCause());
    }

    @Test
    void getCompanyProfileWrapsUriValidationException() throws Exception {
        URIValidationException uriValidationException = new URIValidationException("invalid uri");
        stubApiChain();
        when(companyGet.execute()).thenThrow(uriValidationException);

        ServiceException thrown = assertThrows(
                ServiceException.class,
                () -> companyProfileService.getCompanyProfile(COMPANY_NUMBER));

        assertEquals("Invalid URI for company resource", thrown.getMessage());
        assertSame(uriValidationException, thrown.getCause());
    }

    private void stubApiChain() {
        when(apiClientService.getInternalApiClient()).thenReturn(internalApiClient);
        when(internalApiClient.company()).thenReturn(companyResourceHandler);
        when(companyResourceHandler.get("/company/" + COMPANY_NUMBER)).thenReturn(companyGet);
    }

    private ApiErrorResponseException apiErrorResponseException(int statusCode, String statusMessage) {
        HttpResponseException.Builder builder = new HttpResponseException.Builder(
                statusCode,
                statusMessage,
                new HttpHeaders());
        return new ApiErrorResponseException(builder);
    }
}
