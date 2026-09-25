package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.interceptor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;

@Component
public class InternalUserInterceptor implements HandlerInterceptor {

    private static final String X_REQUEST_ID_HEADER = "X-Request-Id";
    private static final String ERIC_INTERNAL_APP_PRIVILEGES = "ERIC-Authorised-Application-Privileges";
    private static final String INTERNAL_PRIVILEGE_FLAG = "internal_app_privileges";
    private static final String AUTHORIZATION_FAILED_PREFIX = "Authorization failed: requestId=";
    private static final String FORBIDDEN = "Forbidden";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        String requestId = request.getHeader(X_REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = "unknown";
        }

        if (!hasInternalAppPrivileges(request)) {
            LOGGER.error(AUTHORIZATION_FAILED_PREFIX + requestId + ", reason=Missing or invalid internal app privileges flag");
            sendForbiddenResponse(response, requestId);
            return false;
        }

        LOGGER.info("Internal user authorization validated: requestId=" + requestId + ", passing request through");
        return true;
    }

    private static boolean hasInternalAppPrivileges(HttpServletRequest request) {
        String privilegesHeader = request.getHeader(ERIC_INTERNAL_APP_PRIVILEGES);

        try {
            Map<String, Object> privileges = objectMapper.readValue(privilegesHeader, new TypeReference<>() {});
            if (privileges == null) {
                return false;
            }
            Object privilegeFlag = privileges.get(INTERNAL_PRIVILEGE_FLAG);
            return privilegeFlag instanceof Boolean booleanValue && booleanValue;
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.debug("Failed to parse internal app privileges header: " + e.getMessage());
            return false;
        }
    }

    private static void sendForbiddenResponse(HttpServletResponse response, String requestId) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        try {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", HttpStatus.FORBIDDEN);
            errorResponse.put("error", FORBIDDEN);
            errorResponse.put("message", FORBIDDEN);
            errorResponse.put("requestId", requestId);
            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        } catch (IOException e) {
            LOGGER.error("Failed to write error response", e);
        }
    }
}

