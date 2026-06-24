package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import org.springframework.stereotype.Service;
import org.springframework.web.util.UriTemplate;

import uk.gov.companieshouse.api.InternalApiClient;
import uk.gov.companieshouse.api.error.ApiErrorResponseException;
import uk.gov.companieshouse.api.handler.exception.URIValidationException;
import uk.gov.companieshouse.api.model.company.CompanyProfileApi;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.ServiceException;

import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;

@Service
public class CompanyProfileService {

    private static final UriTemplate GET_COMPANY_URI = new UriTemplate("/company/{companyNumber}");

    private final InternalApiClient internalApiClient;

    public CompanyProfileService(InternalApiClient internalApiClient) {
        this.internalApiClient = internalApiClient;
    }

    public CompanyProfileApi getCompanyProfile(String companyNumber) {

        LOGGER.debug("Get company profile for " + companyNumber);

        CompanyProfileApi companyProfileApi;
        String uri = GET_COMPANY_URI.expand(companyNumber).toString();
        String basePath = internalApiClient.getBasePath();
        String fullUrl = (basePath == null ? "" : basePath) + uri;
        String chsApiKey = System.getenv("CHS_API_KEY");
        String keyTail = chsApiKey == null || chsApiKey.length() < 4
                ? "unset"
                : chsApiKey.substring(chsApiKey.length() - 4);
        LOGGER.debug("Company profile request method=GET uri=" + uri + " fullUrl=" + fullUrl);
        LOGGER.debug("Company profile client basePath=" + basePath
                + ", paymentsBasePath=" + internalApiClient.getBasePaymentsPath()
                + ", documentBasePath=" + internalApiClient.getBaseDocumentAPIPath()
                + ", authHeaderConfigured=true"
                + ", chsApiKeyLength=" + (chsApiKey == null ? 0 : chsApiKey.length())
                + ", chsApiKeyLast4=" + keyTail);

        try {
            companyProfileApi = internalApiClient.company().get(uri).execute().getData();
            LOGGER.debug("Company profile response status=200 uri=" + uri);
        } catch (ApiErrorResponseException e) {
            LOGGER.error("Company profile request failed status=" + e.getStatusCode()
                    + " reason=" + e.getStatusMessage()
                    + " uri=" + uri
                    + " response=" + e.getContent());
            throw new ServiceException("Error retrieving company profile", e);
        } catch (URIValidationException e) {
            throw new ServiceException("Invalid URI for company resource", e);
        }

        return companyProfileApi;
    }
}
