package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.interceptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Tag("unit-test")
class AuthenticationInterceptorTest {

    private static final String ERIC_IDENTITY_TYPE_HEADER = "ERIC-Identity-Type";
    private static final String CHS_API_KEY_HEADER = "CHS_API_KEY";
    private static final String X_REQUEST_ID_HEADER = "X-Request-Id";
    private static final String ALLOW_REQUEST_ATTRIBUTE = "ALLOW_REQUEST";
    private static final String REQUEST_ID = "test-request-id-123";
    private static final String VALID_IDENTITY_TYPE = "key";
    private static final String VALID_API_KEY = "test-api-key-123";

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private AuthenticationInterceptor authenticationInterceptor;
    private Object handler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        authenticationInterceptor = new AuthenticationInterceptor();
        handler = new Object();
    }

    @Test
    void validApiKeyAllowsRequest() {
        when(request.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);
        when(request.getHeader(ERIC_IDENTITY_TYPE_HEADER)).thenReturn(VALID_IDENTITY_TYPE);
        when(request.getHeader(CHS_API_KEY_HEADER)).thenReturn(VALID_API_KEY);

        boolean result = authenticationInterceptor.preHandle(request, response, handler);

        assertTrue(result);
    }

    @Test
    void invalidApiKeyReturns401() {
        when(request.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);
        when(request.getHeader(ERIC_IDENTITY_TYPE_HEADER)).thenReturn(VALID_IDENTITY_TYPE);
        when(request.getHeader(CHS_API_KEY_HEADER)).thenReturn(null);

        boolean result = authenticationInterceptor.preHandle(request, response, handler);

        assertFalse(result);
    }

    @Test
    void missingIdentityTypeReturns403() {
        when(request.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);
        when(request.getHeader(ERIC_IDENTITY_TYPE_HEADER)).thenReturn(null);
        when(request.getHeader(CHS_API_KEY_HEADER)).thenReturn(VALID_API_KEY);

        boolean result = authenticationInterceptor.preHandle(request, response, handler);

        assertFalse(result);
    }

    @Test
    void blankIdentityTypeReturns403() {
        when(request.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);
        when(request.getHeader(ERIC_IDENTITY_TYPE_HEADER)).thenReturn("");
        when(request.getHeader(CHS_API_KEY_HEADER)).thenReturn(VALID_API_KEY);

        boolean result = authenticationInterceptor.preHandle(request, response, handler);

        assertFalse(result);
    }

    @Test
    void invalidIdentityTypeReturns403() {
        when(request.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);
        when(request.getHeader(ERIC_IDENTITY_TYPE_HEADER)).thenReturn("invalid");
        when(request.getHeader(CHS_API_KEY_HEADER)).thenReturn(VALID_API_KEY);

        boolean result = authenticationInterceptor.preHandle(request, response, handler);

        assertFalse(result);
    }

    @Test
    void successfulRequestSetsAllowRequestAttribute() {
        when(request.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);
        when(request.getHeader(ERIC_IDENTITY_TYPE_HEADER)).thenReturn(VALID_IDENTITY_TYPE);
        when(request.getHeader(CHS_API_KEY_HEADER)).thenReturn(VALID_API_KEY);

        boolean result = authenticationInterceptor.preHandle(request, response, handler);

        assertTrue(result);
        Object attribute = request.getAttribute(ALLOW_REQUEST_ATTRIBUTE);
        assertNotNull(attribute);
        assertTrue((Boolean) attribute);
    }
}
