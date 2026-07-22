package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import uk.gov.companieshouse.api.util.security.AuthorisationUtil;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils;

import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;

@Component
public class AuthenticationInterceptor implements HandlerInterceptor {

    private static final String X_REQUEST_ID_HEADER = "X-Request-Id";
    private static final String KEY = "key";
    private static final String ERIC_PERMISSIONS_HEADER = "ERIC-Authorised-Application-Permissions";
    private static final String ERIC_PARTNER_ORGANISATION_HEADER = "ERIC-Authorised-Application-Partner-Organisation";
    private static final String AUTHENTICATION_FAILED_PREFIX = "Authentication failed: requestId=";
    private static final String IDENTITY_TYPE_SUFFIX = ", identityType=";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        String requestId = request.getHeader(X_REQUEST_ID_HEADER);
        String identityType = AuthorisationUtil.getAuthorisedIdentityType(request);
        String identityHeader = AuthorisationUtil.getAuthorisedIdentity(request);

        if (!KEY.equals(identityType)) {
            LOGGER.error(AUTHENTICATION_FAILED_PREFIX + requestId + IDENTITY_TYPE_SUFFIX + identityType + ", reason=Invalid ERIC-Identity-Type header");
            sendForbiddenResponse(response, requestId, "Missing or invalid ERIC-Identity-Type header");
            return false;
        }

        if (identityHeader == null || identityHeader.isBlank()) {
            LOGGER.error(AUTHENTICATION_FAILED_PREFIX + requestId + IDENTITY_TYPE_SUFFIX + identityType + ", reason=Missing or invalid API key");
            sendUnauthorizedResponse(response, requestId);
            return false;
        }

        if (!hasRequiredPermission(request)) {
            LOGGER.error(AUTHENTICATION_FAILED_PREFIX + requestId + ", reason=Missing required permission: " + StrikeoffPartnerObjectionsUtils.REQUIRED_ERIC_PERMISSION);
            sendForbiddenResponse(response, requestId, "Missing required permission: " + StrikeoffPartnerObjectionsUtils.REQUIRED_ERIC_PERMISSION);
            return false;
        }

        String partnerOrganisation = getPartnerOrganisation(request);
        if (partnerOrganisation == null) {
            LOGGER.error(AUTHENTICATION_FAILED_PREFIX + requestId + ", reason=Missing required header: " + ERIC_PARTNER_ORGANISATION_HEADER);
            sendForbiddenResponse(response, requestId, "Missing required header: " + ERIC_PARTNER_ORGANISATION_HEADER);
            return false;
        }

        LOGGER.info("Authentication credentials validated: requestId=" + requestId + IDENTITY_TYPE_SUFFIX + identityType + ", partnerOrganisation=" + partnerOrganisation + ", passing request through");
        return true;
    }

    private void sendForbiddenResponse(HttpServletResponse response, String requestId, String message) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        try {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", HttpStatus.FORBIDDEN);
            errorResponse.put("error", "Forbidden");
            errorResponse.put("message", message);
            errorResponse.put("requestId", requestId);
            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        } catch (IOException e) {
            LOGGER.error("Failed to write error response", e);
        }
    }

    private void sendUnauthorizedResponse(HttpServletResponse response, String requestId) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        try {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", HttpStatus.UNAUTHORIZED);
            errorResponse.put("error", "Unauthorized");
            errorResponse.put("message", "Invalid API key");
            errorResponse.put("requestId", requestId);
            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        } catch (IOException e) {
            LOGGER.error("Failed to write error response", e);
        }
    }

    private boolean hasRequiredPermission(HttpServletRequest request) {
        String permissions = request.getHeader(ERIC_PERMISSIONS_HEADER);
        if (permissions == null || permissions.isBlank()) {
            return false;
        }
        return Arrays.asList(permissions.trim().split("\\s+")).contains(StrikeoffPartnerObjectionsUtils.REQUIRED_ERIC_PERMISSION);
    }

    private String getPartnerOrganisation(HttpServletRequest request) {
        String value = request.getHeader(ERIC_PARTNER_ORGANISATION_HEADER);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
