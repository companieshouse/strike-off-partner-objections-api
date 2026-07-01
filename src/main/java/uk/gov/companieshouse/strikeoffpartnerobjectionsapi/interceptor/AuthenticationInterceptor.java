package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import uk.gov.companieshouse.api.util.security.AuthorisationUtil;

import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;

@Component
@ConditionalOnProperty(name = "interceptor.authentication.enabled", havingValue = "true", matchIfMissing = true)
public class AuthenticationInterceptor implements HandlerInterceptor {

    private static final String ERIC_IDENTITY_TYPE_KEY = "key";
    private static final String X_REQUEST_ID_HEADER = "X-Request-Id";
    private static final String ALLOW_REQUEST_ATTRIBUTE = "ALLOW_REQUEST";
    private static final String AUTHENTICATION_FAILED_PREFIX = "Authentication failed: requestId=";
    private static final String IDENTITY_TYPE_SUFFIX = ", identityType=";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String requestId = request.getHeader(X_REQUEST_ID_HEADER);
        String identityType = AuthorisationUtil.getAuthorisedIdentityType(request);
        String identityHeader = AuthorisationUtil.getAuthorisedIdentity(request);

        if (!ERIC_IDENTITY_TYPE_KEY.equals(identityType)) {
            LOGGER.error(AUTHENTICATION_FAILED_PREFIX + requestId + IDENTITY_TYPE_SUFFIX + identityType + ", reason=Invalid ERIC-Identity-Type header");
            sendForbiddenResponse(response, requestId, "Missing or invalid ERIC-Identity-Type header");
            return false;
        }

        if (identityHeader == null || identityHeader.isBlank()) {
            LOGGER.error(AUTHENTICATION_FAILED_PREFIX + requestId + IDENTITY_TYPE_SUFFIX + identityType + ", reason=Missing or invalid API key");
            sendUnauthorizedResponse(response, requestId);
            return false;
        }

        LOGGER.info("Authentication successful: requestId=" + requestId + IDENTITY_TYPE_SUFFIX + identityType);
        request.setAttribute(ALLOW_REQUEST_ATTRIBUTE, true);
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
