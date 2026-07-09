package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import org.springframework.stereotype.Service;
import uk.gov.companieshouse.api.model.company.CompanyProfileApi;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.CompanyValidationException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.ServiceException;

import static java.lang.String.format;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;

import java.util.Set;

/**
 * Service for validating company-related information in withdrawal requests.
 * Validates that the company exists, the name matches, and it has an active proposal to strike off.
 */
@Service
public class CompanyValidator {

    private static final String COMPANY_NUMBER_NOT_EXIST = "COMPANY_NUMBER_NOT_EXIST";
    private static final String SUBMISSION_COMPANY_NAME_MISMATCH = "SUBMISSION_COMPANY_NAME_MISMATCH";
    private static final String INVALID_COMPANY_STATUS = "INVALID_COMPANY_STATUS";
    private static final String INVALID_COMPANY_TYPE = "INVALID_COMPANY_TYPE";

    // Status indicating a strike-off proposal exists for the company
    private static final String ACTIVE_PROPOSAL_TO_STRIKE_OFF = "active-proposal-to-strike-off";

    // Allowed company types for strike-off withdrawals
    private static final Set<String> ALLOWED_COMPANY_TYPES = Set.of(
            "private-unlimited",
            "ltd",
            "plc",
            "old-public-company",
            "private-limited-guarant-nsc-limited-exemption",
            "private-limited-guarant-nsc",
            "private-limited-shares-section-30-exemption",
            "llp",
            "other",
            "united-kingdom-societas",
            "european-public-limited-liability-company-se"
    );

    private final CompanyProfileService companyProfileService;

    public CompanyValidator(CompanyProfileService companyProfileService) {
        this.companyProfileService = companyProfileService;
    }

    /**
     * Validates company information for a withdrawal request.
     *
     * @param companyNumber the company number from the request path
     * @param submissionCompanyName the company name from the request body
     * @throws CompanyValidationException if any validation check fails
     * @throws ServiceException if the Company Profile API call fails
     */
    public void validateCompany(String companyNumber, String submissionCompanyName) {
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

        // Validate company type
        if (!hasValidCompanyType(companyProfile)) {
            throw new CompanyValidationException(
                    format("Company has invalid type: companyNumber=%s, type=%s",
                            companyNumber, companyProfile.getType()),
                    INVALID_COMPANY_TYPE);
        }

        // Validate company has active proposal to strike off
        if (!hasActiveProposalToStrikeOff(companyProfile)) {
            throw new CompanyValidationException(
                    format("Company does not have an active proposal to strike off: companyNumber=%s",
                            companyNumber),
                    INVALID_COMPANY_STATUS);
        }

        LOGGER.info(format("Company validation passed: companyNumber=%s", companyNumber));
    }

    /**
     * Checks if the company has a valid type.
     * Only specific company types are allowed for strike-off withdrawals.
     *
     * @param companyProfile the company profile from the API
     * @return true if the company type is in the allowed list, false otherwise
     */
    private boolean hasValidCompanyType(CompanyProfileApi companyProfile) {
        String type = companyProfile.getType();
        return type != null && ALLOWED_COMPANY_TYPES.contains(type);
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
        return status != null && status.equals(ACTIVE_PROPOSAL_TO_STRIKE_OFF);
    }
}

