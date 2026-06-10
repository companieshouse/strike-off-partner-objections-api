package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils;

import uk.gov.companieshouse.logging.Logger;
import uk.gov.companieshouse.logging.LoggerFactory;

public class StrikeoffPartnerObjectionsUtils {
    private StrikeoffPartnerObjectionsUtils() {
        /* This utility class should not be instantiated */
    }

    public static final String OBJECTION_KIND = "strike-off-partner-objection#objection";
    public static final String APPLICATION_NAMESPACE = "strike-off-partner-objections-api";
    public static final Logger LOGGER = LoggerFactory.getLogger(APPLICATION_NAMESPACE);
}
