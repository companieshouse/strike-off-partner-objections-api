package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import org.springframework.stereotype.Component;

/**
 * Extracts and validates ERIC authentication headers from the current request.
 */
@Component
public class AuthenticationHeaderExtractor {

    public static final String ERIC_PERMISSIONS_HEADER = "ERIC-Authorised-Application-Permissions";
    public static final String ERIC_PARTNER_ORGANISATION_HEADER = "ERIC-Authorised-Application-Partner-Organisation";
    public static final String REQUIRED_PERMISSION = "strike-off-partner-objections";

    private final HttpServletRequest httpServletRequest;

    public AuthenticationHeaderExtractor(HttpServletRequest httpServletRequest) {
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
        return Arrays.asList(permissions.trim().split("\\s+")).contains(REQUIRED_PERMISSION);
    }
}

