package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;

@Component
public class AuthenticationInterceptor implements HandlerInterceptor {

    private static final String ERIC_IDENTITY_TYPE_HEADER = "ERIC-Identity-Type";
    private static final String ERIC_IDENTITY_HEADER = "ERIC-Identity";
    private static final String ERIC_IDENTITY_TYPE_KEY = "key";
    private static final String X_REQUEST_ID_HEADER = "X-Request-Id";
    private static final String ALLOW_REQUEST_ATTRIBUTE = "ALLOW_REQUEST";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String requestId = request.getHeader(X_REQUEST_ID_HEADER);
        String identityType = request.getHeader(ERIC_IDENTITY_TYPE_HEADER);
        String identityHeader = request.getHeader(ERIC_IDENTITY_HEADER);

        if (identityType == null || identityType.isBlank()) {
            LOGGER.error("Authentication failed: requestId=" + requestId + ", reason=Missing ERIC-Identity-Type header");
            sendForbiddenResponse(response, requestId, "Missing or invalid ERIC-Identity-Type header");
            return false;
        }

        if (!ERIC_IDENTITY_TYPE_KEY.equals(identityType)) {
            LOGGER.error("Authentication failed: requestId=" + requestId + ", identityType=" + identityType + ", reason=Invalid ERIC-Identity-Type header");
            sendForbiddenResponse(response, requestId, "Missing or invalid ERIC-Identity-Type header");
            return false;
        }

        if (identityHeader == null || identityHeader.isBlank()) {
            LOGGER.error("Authentication failed: requestId=" + requestId + ", identityType=" + identityType + ", reason=Missing or invalid API key");
            sendUnauthorizedResponse(response, requestId);
            return false;
        }

        LOGGER.info("Authentication successful: requestId=" + requestId + ", identityType=" + identityType);
        request.setAttribute(ALLOW_REQUEST_ATTRIBUTE, true);
        return true;
    }

    private void sendForbiddenResponse(HttpServletResponse response, String requestId, String message) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        try {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", 403);
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
            errorResponse.put("status", 401);
            errorResponse.put("error", "Unauthorized");
            errorResponse.put("message", "Invalid API key");
            errorResponse.put("requestId", requestId);
            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        } catch (IOException e) {
            LOGGER.error("Failed to write error response", e);
        }
    }
}
