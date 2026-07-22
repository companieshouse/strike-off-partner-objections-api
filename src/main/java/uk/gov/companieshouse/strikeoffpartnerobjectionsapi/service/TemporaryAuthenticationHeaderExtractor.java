package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import org.springframework.stereotype.Component;

import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.REQUIRED_ERIC_PERMISSION;

/**
 * Extracts and validates ERIC authentication headers from the current request.
 * Once Team
 */
@Component
public class TemporaryAuthenticationHeaderExtractor {

    public static final String ERIC_PERMISSIONS_HEADER = "ERIC-Authorised-Application-Permissions";
    public static final String ERIC_PARTNER_ORGANISATION_HEADER = "ERIC-Authorised-Application-Partner-Organisation";

    private final HttpServletRequest httpServletRequest;

    public TemporaryAuthenticationHeaderExtractor(HttpServletRequest httpServletRequest) {
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

    /**
     * Checks whether the request contains the required permission in the
     * {@code ERIC-Authorised-Application-Permissions} header.
     *
     * @return {@code true} if the required permission is present, {@code false} otherwise
     */
    public boolean hasRequiredPermission() {
        String permissions = httpServletRequest.getHeader(ERIC_PERMISSIONS_HEADER);
        if (permissions == null || permissions.isBlank()) {
            return false;
        }
        return Arrays.asList(permissions.trim().split("\\s+")).contains(REQUIRED_ERIC_PERMISSION);
    }
}

