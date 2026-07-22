package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Provides access to the authenticated partner organisation extracted from the current request.
 */
@Component
public class PartnerOrganisationProvider {

    public static final String ERIC_PARTNER_ORGANISATION_HEADER = "ERIC-Authorised-Application-Partner-Organisation";

    private final HttpServletRequest httpServletRequest;

    public PartnerOrganisationProvider(HttpServletRequest httpServletRequest) {
        this.httpServletRequest = httpServletRequest;
    }

    /**
     * Returns the partner organisation from the ERIC header, or {@code null} if absent or blank.
     */
    public String getPartnerOrganisation() {
        String value = httpServletRequest.getHeader(ERIC_PARTNER_ORGANISATION_HEADER);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

