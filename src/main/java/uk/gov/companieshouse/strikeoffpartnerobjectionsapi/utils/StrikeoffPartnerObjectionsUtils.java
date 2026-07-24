package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.companieshouse.logging.Logger;
import uk.gov.companieshouse.logging.LoggerFactory;

public class StrikeoffPartnerObjectionsUtils {

    private StrikeoffPartnerObjectionsUtils() {
        /* This utility class should not be instantiated */
    }

    public static final String APPLICATION_NAMESPACE = "strike-off-partner-objections-api";
    public static final Logger LOGGER = LoggerFactory.getLogger(APPLICATION_NAMESPACE);
    public static final String REQUIRED_ERIC_PERMISSION = "strike-off-partner-objections";
    public static final String ERIC_PARTNER_ORGANISATION_HEADER = "ERIC-Authorised-Application-Partner-Organisation";
    // For use within test files only
    public static final String PARTNER_ORGANISATION = "hmrc";

    public static String validatePartnerOrganisation(String partnerOrganisation) {
        if (partnerOrganisation == null || partnerOrganisation.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Missing or blank partner organisation header");
        }
        return partnerOrganisation.trim();
    }
}
