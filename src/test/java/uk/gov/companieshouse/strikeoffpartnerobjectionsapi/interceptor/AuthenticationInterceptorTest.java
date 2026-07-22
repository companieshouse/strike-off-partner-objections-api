package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.interceptor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service.AuthenticationService;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service.PartnerOrganisationProvider;

@Tag("unit-test")
class AuthenticationInterceptorTest {

    private static final String ERIC_IDENTITY_TYPE_HEADER = "ERIC-Identity-Type";
    private static final String ERIC_IDENTITY_HEADER = "ERIC-Identity";
    private static final String X_REQUEST_ID_HEADER = "X-Request-Id";
    private static final String ERIC_PERMISSIONS_HEADER = "ERIC-Authorised-Application-Permissions";
    private static final String ERIC_PARTNER_ORG_HEADER = PartnerOrganisationProvider.ERIC_PARTNER_ORGANISATION_HEADER;
    private static final String REQUEST_ID = "test-request-id-123";
    private static final String VALID_IDENTITY_TYPE = "key";
    private static final String VALID_API_KEY = "test-api-key-123";
    private static final String VALID_PERMISSION = "strike-off-partner-objections";
    private static final String VALID_PARTNER_ORG = "HMRC";

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private AuthenticationInterceptor authenticationInterceptor;
    private Object handler;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        authenticationInterceptor = new AuthenticationInterceptor(
                new AuthenticationService(),
                new PartnerOrganisationProvider(request));
        handler = new Object();

        // Setup mock response to handle getWriter() calls
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(printWriter);
    }

    @Test
    void preHandle_whenAllHeadersAreValid_allowsRequest() {
        setupValidHeaders();

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
    void preHandle_whenPermissionsHeaderIsMissing_returns403() {
        when(request.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);
        when(request.getHeader(ERIC_IDENTITY_TYPE_HEADER)).thenReturn(VALID_IDENTITY_TYPE);
        when(request.getHeader(ERIC_IDENTITY_HEADER)).thenReturn(VALID_API_KEY);
        when(request.getHeader(ERIC_PERMISSIONS_HEADER)).thenReturn(null);
        when(request.getHeader(ERIC_PARTNER_ORG_HEADER)).thenReturn(VALID_PARTNER_ORG);

        boolean result = authenticationInterceptor.preHandle(request, response, handler);

        assertFalse(result);
    }

    @Test
    void preHandle_whenRequiredPermissionIsAbsent_returns403() {
        when(request.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);
        when(request.getHeader(ERIC_IDENTITY_TYPE_HEADER)).thenReturn(VALID_IDENTITY_TYPE);
        when(request.getHeader(ERIC_IDENTITY_HEADER)).thenReturn(VALID_API_KEY);
        when(request.getHeader(ERIC_PERMISSIONS_HEADER)).thenReturn("other-permission");
        when(request.getHeader(ERIC_PARTNER_ORG_HEADER)).thenReturn(VALID_PARTNER_ORG);

        boolean result = authenticationInterceptor.preHandle(request, response, handler);

        assertFalse(result);
    }

    @Test
    void preHandle_whenPartnerOrganisationHeaderIsMissing_returns403() {
        when(request.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);
        when(request.getHeader(ERIC_IDENTITY_TYPE_HEADER)).thenReturn(VALID_IDENTITY_TYPE);
        when(request.getHeader(ERIC_IDENTITY_HEADER)).thenReturn(VALID_API_KEY);
        when(request.getHeader(ERIC_PERMISSIONS_HEADER)).thenReturn(VALID_PERMISSION);
        when(request.getHeader(ERIC_PARTNER_ORG_HEADER)).thenReturn(null);

        boolean result = authenticationInterceptor.preHandle(request, response, handler);

        assertFalse(result);
    }

    @Test
    void preHandle_whenPartnerOrganisationHeaderIsBlank_returns403() {
        when(request.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);
        when(request.getHeader(ERIC_IDENTITY_TYPE_HEADER)).thenReturn(VALID_IDENTITY_TYPE);
        when(request.getHeader(ERIC_IDENTITY_HEADER)).thenReturn(VALID_API_KEY);
        when(request.getHeader(ERIC_PERMISSIONS_HEADER)).thenReturn(VALID_PERMISSION);
        when(request.getHeader(ERIC_PARTNER_ORG_HEADER)).thenReturn("   ");

        boolean result = authenticationInterceptor.preHandle(request, response, handler);

        assertFalse(result);
    }

    @Test
    void preHandle_whenRequiredPermissionIsPresentAmongMultiple_allowsRequest() {
        when(request.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);
        when(request.getHeader(ERIC_IDENTITY_TYPE_HEADER)).thenReturn(VALID_IDENTITY_TYPE);
        when(request.getHeader(ERIC_IDENTITY_HEADER)).thenReturn(VALID_API_KEY);
        when(request.getHeader(ERIC_PERMISSIONS_HEADER)).thenReturn("other-permission " + VALID_PERMISSION + " another");
        when(request.getHeader(ERIC_PARTNER_ORG_HEADER)).thenReturn(VALID_PARTNER_ORG);

        boolean result = authenticationInterceptor.preHandle(request, response, handler);

        assertTrue(result);
    }

    private void setupValidHeaders() {
        when(request.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);
        when(request.getHeader(ERIC_IDENTITY_TYPE_HEADER)).thenReturn(VALID_IDENTITY_TYPE);
        when(request.getHeader(ERIC_IDENTITY_HEADER)).thenReturn(VALID_API_KEY);
        when(request.getHeader(ERIC_PERMISSIONS_HEADER)).thenReturn(VALID_PERMISSION);
        when(request.getHeader(ERIC_PARTNER_ORG_HEADER)).thenReturn(VALID_PARTNER_ORG);
    }
}
