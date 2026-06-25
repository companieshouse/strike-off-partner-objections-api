package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import org.springframework.stereotype.Service;
import org.springframework.web.util.UriTemplate;

import uk.gov.companieshouse.api.error.ApiErrorResponseException;
import uk.gov.companieshouse.api.handler.exception.URIValidationException;
import uk.gov.companieshouse.api.model.company.CompanyProfileApi;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.ServiceException;

import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;

@Service
public class CompanyProfileService {

    private static final UriTemplate GET_COMPANY_URI = new UriTemplate("/company/{companyNumber}");
    private ApiClientService apiClientService;
    
    public CompanyProfileService(ApiClientService apiClientService) {
        this.apiClientService = apiClientService;
    }

    public CompanyProfileApi getCompanyProfile(String companyNumber) {
        LOGGER.debug("Get company profile for " + companyNumber);

        String uri = GET_COMPANY_URI.expand(companyNumber).toString();

        try {
            return apiClientService.getInternalApiClient().company().get(uri).execute().getData();
        } catch (ApiErrorResponseException e) {
            throw new ServiceException("Error retrieving company profile", e);
        } catch (URIValidationException e) {
            throw new ServiceException("Invalid URI for company resource", e);
        }
    }
}
