package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import org.springframework.stereotype.Service;
import uk.gov.companieshouse.api.model.company.CompanyProfileApi;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.CompanyValidationException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.ServiceException;

import static java.lang.String.format;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;

/**
 * Service for validating company-related information in withdrawal requests.
 * Validates that the company exists, the name matches, and it has an active proposal to strike off.
 */
@Service
public class CompanyValidator {

    private static final String COMPANY_NUMBER_NOT_EXIST = "COMPANY_NUMBER_NOT_EXIST";
    private static final String SUBMISSION_COMPANY_NAME_MISMATCH = "SUBMISSION_COMPANY_NAME_MISMATCH";
    private static final String INVALID_COMPANY_STATUS = "INVALID_COMPANY_STATUS";

    // Status indicating a strike-off proposal exists for the company
    private static final String DISSOLUTION_PROPOSAL_ACTIVE = "dissolution-proposal-active";

    private final CompanyProfileService companyProfileService;

    public CompanyValidator(CompanyProfileService companyProfileService) {
        this.companyProfileService = companyProfileService;
    }

    /**
     * Validates company information for a withdrawal request.
     *
     * @param companyNumber the company number from the request path
     * @param submissionCompanyName the company name from the request body
     * @return true when all validation checks pass
     * @throws CompanyValidationException if any validation check fails
     * @throws ServiceException if the Company Profile API call fails
     */
    public boolean validateCompany(String companyNumber, String submissionCompanyName) {
        LOGGER.info(format("Validating company: companyNumber=%s, submissionCompanyName=%s",
                companyNumber, submissionCompanyName));

        CompanyProfileApi companyProfile = companyProfileService.getCompanyProfile(companyNumber);

        // Validate company exists
        if (companyProfile == null) {
            throw new CompanyValidationException(
                    format("Company not found: companyNumber=%s", companyNumber),
                    COMPANY_NUMBER_NOT_EXIST);
        }

        // Validate company name matches
        String retrievedName = companyProfile.getCompanyName();
        if (retrievedName == null || !retrievedName.equalsIgnoreCase(submissionCompanyName)) {
            throw new CompanyValidationException(
                    format("Company name mismatch: expected=%s, provided=%s",
                            retrievedName, submissionCompanyName),
                    SUBMISSION_COMPANY_NAME_MISMATCH);
        }

        // Validate company has active proposal to strike off
        if (!hasActiveProposalToStrikeOff(companyProfile)) {
            throw new CompanyValidationException(
                    format("Company does not have an active proposal to strike off: companyNumber=%s",
                            companyNumber),
                    INVALID_COMPANY_STATUS);
        }

        LOGGER.info(format("Company validation passed: companyNumber=%s", companyNumber));
        return true;
    }

    /**
     * Checks if the company has an active proposal to strike off.
     * This is determined by checking the companyStatus field.
     *
     * @param companyProfile the company profile from the API
     * @return true if the company has an active proposal to strike off, false otherwise
     */
    private boolean hasActiveProposalToStrikeOff(CompanyProfileApi companyProfile) {
        String status = companyProfile.getCompanyStatus();
        return status != null && status.equals(DISSOLUTION_PROPOSAL_ACTIVE);
    }
}

