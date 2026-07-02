package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.interceptor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Tag("unit-test")
class AuthenticationInterceptorTest {

    private static final String ERIC_IDENTITY_TYPE_HEADER = "ERIC-Identity-Type";
    private static final String ERIC_IDENTITY_HEADER = "ERIC-Identity";
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
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        authenticationInterceptor = new AuthenticationInterceptor();
        handler = new Object();
        
        // Setup mock response to handle getWriter() calls
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);
        
        // Setup mock request to handle setAttribute/getAttribute
        Map<String, Object> attributes = new HashMap<>();
        doAnswer(invocation -> {
            attributes.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(request).setAttribute(any(), any());
        when(request.getAttribute(any())).thenAnswer(
            invocation -> attributes.get(invocation.getArgument(0))
        );
    }

    @Test
    void preHandle_whenValidApiKey_allowsRequest() {
        when(request.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);
        when(request.getHeader(ERIC_IDENTITY_TYPE_HEADER)).thenReturn(VALID_IDENTITY_TYPE);
        when(request.getHeader(ERIC_IDENTITY_HEADER)).thenReturn(VALID_API_KEY);

        boolean result = authenticationInterceptor.preHandle(request, response, handler);

        assertTrue(result);
    }

    @Test
    void preHandle_whenInvalidApiKey_returns401() {
        when(request.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);
        when(request.getHeader(ERIC_IDENTITY_TYPE_HEADER)).thenReturn(VALID_IDENTITY_TYPE);
        when(request.getHeader(ERIC_IDENTITY_HEADER)).thenReturn(null);

        boolean result = authenticationInterceptor.preHandle(request, response, handler);

        assertFalse(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "invalid"})
    void preHandle_whenInvalidIdentityType_returns403(String invalidType) {
        when(request.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);
        when(request.getHeader(ERIC_IDENTITY_TYPE_HEADER)).thenReturn(invalidType);
        when(request.getHeader(ERIC_IDENTITY_HEADER)).thenReturn(VALID_API_KEY);

        boolean result = authenticationInterceptor.preHandle(request, response, handler);

        assertFalse(result);
    }

    @Test
    void preHandle_whenMissingIdentityType_returns403() {
        when(request.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);
        when(request.getHeader(ERIC_IDENTITY_TYPE_HEADER)).thenReturn(null);
        when(request.getHeader(ERIC_IDENTITY_HEADER)).thenReturn(VALID_API_KEY);

        boolean result = authenticationInterceptor.preHandle(request, response, handler);

        assertFalse(result);
    }

    @Test
    void preHandle_whenSuccessfulRequest_setsAllowRequestAttribute() {
        when(request.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);
        when(request.getHeader(ERIC_IDENTITY_TYPE_HEADER)).thenReturn(VALID_IDENTITY_TYPE);
        when(request.getHeader(ERIC_IDENTITY_HEADER)).thenReturn(VALID_API_KEY);

        boolean result = authenticationInterceptor.preHandle(request, response, handler);

        assertTrue(result);
        Object attribute = request.getAttribute(ALLOW_REQUEST_ATTRIBUTE);
        assertNotNull(attribute);
        assertTrue((Boolean) attribute);
    }
}
