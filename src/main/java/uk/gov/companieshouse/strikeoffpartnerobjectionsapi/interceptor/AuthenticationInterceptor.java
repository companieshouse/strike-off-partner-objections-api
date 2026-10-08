package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import uk.gov.companieshouse.logging.util.DataMap;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import uk.gov.companieshouse.api.util.security.AuthorisationUtil;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils;

import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.ERIC_PARTNER_ORGANISATION_HEADER;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;

@Component
public class AuthenticationInterceptor implements HandlerInterceptor {

    private static final String X_REQUEST_ID_HEADER = "X-Request-Id";
    private static final String KEY = "key";
    private static final String ERIC_PERMISSIONS_HEADER = "ERIC-Authorised-Application-Permissions";
    private static final String AUTHENTICATION_VALIDATION_FAILED = "Authentication validation failed";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        String requestId = request.getHeader(X_REQUEST_ID_HEADER);
        String identityType = AuthorisationUtil.getAuthorisedIdentityType(request);
        String identityHeader = AuthorisationUtil.getAuthorisedIdentity(request);

        if (!KEY.equals(identityType)) {
            var logMap = new DataMap.Builder()
                    .requestId(requestId)
                    .errorMessage("Invalid ERIC-Identity-Type header: " + identityType)
                    .build()
                    .getLogMap();
            LOGGER.error(AUTHENTICATION_VALIDATION_FAILED, logMap);
            sendForbiddenResponse(response, requestId, "Missing or invalid ERIC-Identity-Type header");
            return false;
        }

        if (identityHeader == null || identityHeader.isBlank()) {
            var logMap = new DataMap.Builder()
                    .requestId(requestId)
                    .errorMessage("Missing or invalid API key")
                    .build()
                    .getLogMap();
            LOGGER.error(AUTHENTICATION_VALIDATION_FAILED, logMap);
            sendUnauthorizedResponse(response, requestId);
            return false;
        }

        if (!hasRequiredPermission(request)) {
            var logMap = new DataMap.Builder()
                    .requestId(requestId)
                    .errorMessage("Missing required permission: " + StrikeoffPartnerObjectionsUtils.REQUIRED_ERIC_PERMISSION)
                    .build()
                    .getLogMap();
            LOGGER.error(AUTHENTICATION_VALIDATION_FAILED, logMap);
            sendForbiddenResponse(response, requestId, "Missing required permission: " + StrikeoffPartnerObjectionsUtils.REQUIRED_ERIC_PERMISSION);
            return false;
        }

        String partnerOrganisation = getPartnerOrganisation(request);
        if (partnerOrganisation == null) {
            var logMap = new DataMap.Builder()
                    .requestId(requestId)
                    .errorMessage("Missing required header: " + ERIC_PARTNER_ORGANISATION_HEADER)
                    .build()
                    .getLogMap();
            LOGGER.error(AUTHENTICATION_VALIDATION_FAILED, logMap);
            sendForbiddenResponse(response, requestId, "Missing required header: " + ERIC_PARTNER_ORGANISATION_HEADER);
            return false;
        }

        var logMap = new DataMap.Builder()
                .requestId(requestId)
                .partnerOrganisation(partnerOrganisation)
                .build()
                .getLogMap();
        LOGGER.info("Authentication credentials validated and request passed through", logMap);
        return true;
    }

    private static void sendForbiddenResponse(HttpServletResponse response, String requestId, String message) {
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
            var logMap = new DataMap.Builder()
                    .requestId(requestId)
                    .errorMessage("IOException whilst writing forbidden response")
                    .build()
                    .getLogMap();
            LOGGER.error("Failed to write forbidden error response", e, logMap);
        }
    }

    private static void sendUnauthorizedResponse(HttpServletResponse response, String requestId) {
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
            var logMap = new DataMap.Builder()
                    .requestId(requestId)
                    .errorMessage("IOException whilst writing unauthorized response")
                    .build()
                    .getLogMap();
            LOGGER.error("Failed to write unauthorized error response", e, logMap);
        }
    }

    private static boolean hasRequiredPermission(HttpServletRequest request) {
        String permissions = request.getHeader(ERIC_PERMISSIONS_HEADER);
        if (permissions == null || permissions.isBlank()) {
            return false;
        }
        return Arrays.asList(permissions.trim().split("\\s+")).contains(StrikeoffPartnerObjectionsUtils.REQUIRED_ERIC_PERMISSION);
    }

    private static String getPartnerOrganisation(HttpServletRequest request) {
        String value = request.getHeader(ERIC_PARTNER_ORGANISATION_HEADER);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
