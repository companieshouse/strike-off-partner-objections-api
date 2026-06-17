package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import org.springframework.stereotype.Service;
import uk.gov.companieshouse.api.ApiClient;
import uk.gov.companieshouse.api.error.ApiErrorResponseException;
import uk.gov.companieshouse.api.handler.exception.URIValidationException;
import uk.gov.companieshouse.api.model.ApiResponse;
import uk.gov.companieshouse.api.model.company.CompanyProfileApi;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.CompanyProfileServiceException;

import static java.lang.String.format;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;

@Service
public class CompanyProfileService {

    private final ApiClient apiClient;

    public CompanyProfileService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public CompanyProfileApi getCompanyProfile(final String companyNumber) {
        LOGGER.info(format("Fetching company profile: companyNumber=%s", companyNumber));
        try {
            ApiResponse<CompanyProfileApi> response = apiClient
                    .company()
                    .get(companyNumber)
                    .execute();
            return response.getData();
        } catch (ApiErrorResponseException ex) {
            throw new CompanyProfileServiceException(
                    format("Error response from company-profile-api for company %s: status=%d",
                            companyNumber, ex.getStatusCode()), ex);
        } catch (URIValidationException ex) {
            throw new CompanyProfileServiceException(
                    format("Invalid URI when fetching company profile for company %s", companyNumber), ex);
        }
    }
}

