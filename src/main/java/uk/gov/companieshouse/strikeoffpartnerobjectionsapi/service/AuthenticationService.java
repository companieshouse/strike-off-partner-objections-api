package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import org.springframework.stereotype.Service;

/**
 * Service responsible for validating ERIC authentication headers.
 */
@Service
public class AuthenticationService {

    public static final String ERIC_PERMISSIONS_HEADER = "ERIC-Authorised-Application-Permissions";
    public static final String REQUIRED_PERMISSION = "strike-off-partner-objections";

    /**
     * Checks whether the request contains the required permission in the
     * {@code ERIC-Authorised-Application-Permissions} header.
     *
     * @param request the incoming HTTP request
     * @return {@code true} if the required permission is present, {@code false} otherwise
     */
    public boolean hasRequiredPermission(HttpServletRequest request) {
        String permissions = request.getHeader(ERIC_PERMISSIONS_HEADER);
        if (permissions == null || permissions.isBlank()) {
            return false;
        }
        return Arrays.asList(permissions.trim().split("\\s+")).contains(REQUIRED_PERMISSION);
    }
}

