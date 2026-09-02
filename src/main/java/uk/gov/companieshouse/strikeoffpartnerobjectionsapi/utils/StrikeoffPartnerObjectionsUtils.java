package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.companieshouse.logging.Logger;
import uk.gov.companieshouse.logging.LoggerFactory;

/**
 * Shared utility constants and helper methods for the Strike-Off Partner Objections API.
 *
 * <p>Provides the application namespace, structured logger, required Eric permission string,
 * the partner organisation header name, and a validation helper used by controllers.</p>
 */
public final class StrikeoffPartnerObjectionsUtils {

    private StrikeoffPartnerObjectionsUtils() {
        /* This utility class should not be instantiated */
    }

    public static final String APPLICATION_NAMESPACE = "strike-off-partner-objections-api";
    public static final Logger LOGGER = LoggerFactory.getLogger(APPLICATION_NAMESPACE);
    public static final String REQUIRED_ERIC_PERMISSION = "strike-off-partner-objections";
    public static final String ERIC_PARTNER_ORGANISATION_HEADER = "ERIC-Authorised-Application-Partner-Organisation";
    // For use within test files only
    public static final String PARTNER_ORGANISATION = "hmrc";

    /**
     * Validates that the partner organisation header value is present and non-blank.
     *
     * <p>Returns the trimmed value on success; throws a {@code 403 Forbidden}
     * {@link ResponseStatusException} if the value is {@code null} or blank.</p>
     *
     * @param partnerOrganisation the raw header value extracted from the HTTP request
     * @return the trimmed, non-blank partner organisation string
     * @throws ResponseStatusException with HTTP 403 if the header is missing or blank
     */
    public static String validatePartnerOrganisation(String partnerOrganisation) {
        if (partnerOrganisation == null || partnerOrganisation.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Missing or blank partner organisation header");
        }
        return partnerOrganisation.trim();
    }
}
