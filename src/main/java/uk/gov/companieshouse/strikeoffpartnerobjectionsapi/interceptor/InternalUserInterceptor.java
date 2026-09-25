package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.ERIC_AUTHORISED_KEY_PRIVILEGES;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.INTERNAL_APP_PRIVILEGE;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;

/**
 * Interceptor to validate internal app privileges for internal endpoints.
 *
 * <p>Checks that API keys making requests to internal endpoints have the
 * "internal-app" privilege in the ERIC-Authorised-Key-Privileges header.
 * Returns HTTP 403 Forbidden if the privilege is not present.</p>
 */
@Component
public class InternalUserInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @Nullable Object handler) {
        String privilegesHeader = request.getHeader(ERIC_AUTHORISED_KEY_PRIVILEGES);

        if (privilegesHeader == null || !hasInternalAppPrivilege(privilegesHeader)) {
            LOGGER.error("Request to internal endpoint missing internal-app privilege");
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return false;
        }

        return true;
    }

    /**
     * Checks if the privileges header contains the internal-app privilege.
     *
     * <p>The ERIC-Authorised-Key-Privileges header contains a comma-separated list
     * of privileges (e.g., "internal-app,payment"). This method checks if
     * "internal-app" is one of those privileges.</p>
     *
     * @param privilegesHeader the comma-separated privilege string from the header
     * @return true if "internal-app" is present in the privileges list
     */
    private boolean hasInternalAppPrivilege(@NonNull String privilegesHeader) {
        if (privilegesHeader.isBlank()) {
            return false;
        }

        String[] privileges = privilegesHeader.split(",");
        for (String privilege : privileges) {
            if (INTERNAL_APP_PRIVILEGE.equals(privilege.trim())) {
                return true;
            }
        }
        return false;
    }
}
