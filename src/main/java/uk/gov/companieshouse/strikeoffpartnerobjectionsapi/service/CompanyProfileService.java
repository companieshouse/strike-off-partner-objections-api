package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import org.springframework.stereotype.Service;
import org.springframework.web.util.UriTemplate;

import uk.gov.companieshouse.api.error.ApiErrorResponseException;
import uk.gov.companieshouse.api.handler.exception.URIValidationException;
import uk.gov.companieshouse.api.model.company.CompanyProfileApi;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.ServiceException;

import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;

/**
 * Service for fetching company profile information from the internal Companies House API.
 *
 * <p>Returns {@code null} when the company is not found (HTTP 404) so that callers can
 * apply their own not-found handling. All other API errors are wrapped in a
 * {@link ServiceException}.</p>
 */
@Service
public class CompanyProfileService {

    private static final UriTemplate GET_COMPANY_URI = new UriTemplate("/company/{companyNumber}");
    private final ApiClientService apiClientService;
    
    /**
     * Constructs the service with the API client wrapper.
     *
     * @param apiClientService the service providing the internal API client
     */
    public CompanyProfileService(ApiClientService apiClientService) {
        this.apiClientService = apiClientService;
    }

    /**
     * Retrieves the company profile for the given company number.
     *
     * @param companyNumber the Companies House company number to look up
     * @return the company profile, or {@code null} if the company is not found
     * @throws ServiceException if the API call fails for any reason other than a 404 response
     */
    public CompanyProfileApi getCompanyProfile(String companyNumber) {
        LOGGER.debug(String.format("Get company profile for %s", companyNumber));

        String uri = GET_COMPANY_URI.expand(companyNumber).toString();

        try {
            return apiClientService.getInternalApiClient().company().get(uri).execute().getData();
        } catch (ApiErrorResponseException e) {
            if (e.getStatusCode() == 404) {
                LOGGER.debug(String.format("Company not found for company number %s", companyNumber));
                return null;
            }
            throw new ServiceException("Error retrieving company profile", e);
        } catch (URIValidationException e) {
            throw new ServiceException("Invalid URI for company resource", e);
        }
    }
}
