package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.companieshouse.api.error.ApiErrorResponseException;
import uk.gov.companieshouse.api.model.company.CompanyProfileApi;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.CompanyValidationException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.ServiceException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;

@Tag("unit-test")
@ExtendWith(MockitoExtension.class)
class CompanyValidatorTest {

    private static final String COMPANY_NUMBER = "12345678";
    private static final String COMPANY_NAME = "ACME Ltd";

    @Mock
    private CompanyProfileService companyProfileService;

    private CompanyValidator companyValidator;

    @BeforeEach
    void setUp() {
        companyValidator = new CompanyValidator(companyProfileService);
    }

    @Test
    void validateCompany_whenCompanyNotFound_throwsCompanyValidationException() {
        when(companyProfileService.getCompanyProfile(COMPANY_NUMBER)).thenReturn(null);

        CompanyValidationException thrown = assertThrows(
                CompanyValidationException.class,
                () -> companyValidator.validateCompany(COMPANY_NUMBER, COMPANY_NAME));

        assertEquals("COMPANY_NUMBER_NOT_EXIST", thrown.getErrorCode());
    }

    @Test
    void validateCompany_whenCompanyNameDoesNotMatch_throwsCompanyValidationException() {
        CompanyProfileApi companyProfile = new CompanyProfileApi();
        companyProfile.setCompanyName("DIFFERENT LTD");
        companyProfile.setCompanyStatus("dissolution-proposal-active");

        when(companyProfileService.getCompanyProfile(COMPANY_NUMBER)).thenReturn(companyProfile);

        CompanyValidationException thrown = assertThrows(
                CompanyValidationException.class,
                () -> companyValidator.validateCompany(COMPANY_NUMBER, COMPANY_NAME));

        assertEquals("SUBMISSION_COMPANY_NAME_MISMATCH", thrown.getErrorCode());
    }

    @Test
    void validateCompany_whenCompanyNameIsNull_throwsCompanyValidationException() {
        CompanyProfileApi companyProfile = new CompanyProfileApi();
        companyProfile.setCompanyName(null);
        companyProfile.setCompanyStatus("dissolution-proposal-active");

        when(companyProfileService.getCompanyProfile(COMPANY_NUMBER)).thenReturn(companyProfile);

        CompanyValidationException thrown = assertThrows(
                CompanyValidationException.class,
                () -> companyValidator.validateCompany(COMPANY_NUMBER, COMPANY_NAME));

        assertEquals("SUBMISSION_COMPANY_NAME_MISMATCH", thrown.getErrorCode());
    }

    @Test
    void validateCompany_whenCompanyNameMatchesIgnoringCase_succeeds() {
        CompanyProfileApi companyProfile = new CompanyProfileApi();
        companyProfile.setCompanyName("acme ltd");
        companyProfile.setType("llp");
        companyProfile.setCompanyStatus("active-proposal-to-strike-off");

        when(companyProfileService.getCompanyProfile(COMPANY_NUMBER)).thenReturn(companyProfile);

        companyValidator.validateCompany(COMPANY_NUMBER, COMPANY_NAME);
    }

    @ParameterizedTest
    @ValueSource(strings = {"private-unlimited", "ltd", "plc", "llp", "old-public-company",
            "private-limited-guarant-nsc-limited-exemption", "private-limited-guarant-nsc",
            "private-limited-shares-section-30-exemption", "other", "united-kingdom-societas",
            "european-public-limited-liability-company-se"})
    void validateCompany_whenCompanyTypeIsValid_succeeds(String validType) {
        CompanyProfileApi companyProfile = new CompanyProfileApi();
        companyProfile.setCompanyName(COMPANY_NAME);
        companyProfile.setType(validType);
        companyProfile.setCompanyStatus("active-proposal-to-strike-off");

        when(companyProfileService.getCompanyProfile(COMPANY_NUMBER)).thenReturn(companyProfile);

        companyValidator.validateCompany(COMPANY_NUMBER, COMPANY_NAME);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sole-trader", "partnership", "unregistered-company", "investment-entity"})
    @NullSource
    void validateCompany_whenCompanyTypeIsInvalidOrNull_throwsCompanyValidationException(String invalidType) {
        CompanyProfileApi companyProfile = new CompanyProfileApi();
        companyProfile.setCompanyName(COMPANY_NAME);
        companyProfile.setType(invalidType);
        companyProfile.setCompanyStatus("active-proposal-to-strike-off");

        when(companyProfileService.getCompanyProfile(COMPANY_NUMBER)).thenReturn(companyProfile);

        CompanyValidationException thrown = assertThrows(
                CompanyValidationException.class,
                () -> companyValidator.validateCompany(COMPANY_NUMBER, COMPANY_NAME));

        assertEquals("INVALID_COMPANY_TYPE", thrown.getErrorCode());
    }

    @Test
    void validateCompany_whenCompanyDoesNotHaveActiveProposalToStrikeOff_throwsCompanyValidationException() {
        CompanyProfileApi companyProfile = new CompanyProfileApi();
        companyProfile.setCompanyName(COMPANY_NAME);
        companyProfile.setType("llp");
        companyProfile.setCompanyStatus("active");

        when(companyProfileService.getCompanyProfile(COMPANY_NUMBER)).thenReturn(companyProfile);

        CompanyValidationException thrown = assertThrows(
                CompanyValidationException.class,
                () -> companyValidator.validateCompany(COMPANY_NUMBER, COMPANY_NAME));

        assertEquals("INVALID_COMPANY_STATUS", thrown.getErrorCode());
    }

    @Test
    void validateCompany_whenCompanyStatusIsNull_throwsCompanyValidationException() {
        CompanyProfileApi companyProfile = new CompanyProfileApi();
        companyProfile.setCompanyName(COMPANY_NAME);
        companyProfile.setType("llp");
        companyProfile.setCompanyStatus(null);

        when(companyProfileService.getCompanyProfile(COMPANY_NUMBER)).thenReturn(companyProfile);

        CompanyValidationException thrown = assertThrows(
                CompanyValidationException.class,
                () -> companyValidator.validateCompany(COMPANY_NUMBER, COMPANY_NAME));

        assertEquals("INVALID_COMPANY_STATUS", thrown.getErrorCode());
    }

    @Test
    void validateCompany_whenAllValidationsPass_succeeds() {
        CompanyProfileApi companyProfile = new CompanyProfileApi();
        companyProfile.setCompanyName(COMPANY_NAME);
        companyProfile.setType("llp");
        companyProfile.setCompanyStatus("active-proposal-to-strike-off");

        when(companyProfileService.getCompanyProfile(COMPANY_NUMBER)).thenReturn(companyProfile);

        companyValidator.validateCompany(COMPANY_NUMBER, COMPANY_NAME);
    }

    @Test
    void validateCompany_whenCompanyProfileServiceThrowsServiceException_propagatesException() {
        ApiErrorResponseException apiError = apiErrorResponseException();
        ServiceException serviceException = new ServiceException("Error retrieving company profile", apiError);

        when(companyProfileService.getCompanyProfile(COMPANY_NUMBER)).thenThrow(serviceException);

        ServiceException thrown = assertThrows(
                ServiceException.class,
                () -> companyValidator.validateCompany(COMPANY_NUMBER, COMPANY_NAME));

        assertEquals("Error retrieving company profile", thrown.getMessage());
    }

    private ApiErrorResponseException apiErrorResponseException() {
        HttpResponseException.Builder builder = new HttpResponseException.Builder(
                502,
                "Bad Gateway",
                new HttpHeaders());
        return new ApiErrorResponseException(builder);
    }
}
