package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.interceptor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.ERIC_PARTNER_ORGANISATION_HEADER;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.stream.Stream;

@Tag("unit-test")
class AuthenticationInterceptorTest {

    private static final String ERIC_IDENTITY_TYPE_HEADER = "ERIC-Identity-Type";
    private static final String ERIC_IDENTITY_HEADER = "ERIC-Identity";
    private static final String X_REQUEST_ID_HEADER = "X-Request-Id";
    private static final String ERIC_PERMISSIONS_HEADER = "ERIC-Authorised-Application-Permissions";
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
        authenticationInterceptor = new AuthenticationInterceptor();
        handler = new Object();

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

    @ParameterizedTest
    @MethodSource("invalidIdentityTypes")
    void preHandle_whenIdentityTypeIsInvalid_returns403(String identityType) {
        when(request.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);
        when(request.getHeader(ERIC_IDENTITY_TYPE_HEADER)).thenReturn(identityType);
        when(request.getHeader(ERIC_IDENTITY_HEADER)).thenReturn(VALID_API_KEY);

        boolean result = authenticationInterceptor.preHandle(request, response, handler);

        assertFalse(result);
    }

    @Test
    void preHandle_whenApiKeyIsMissing_returns401() {
        when(request.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);
        when(request.getHeader(ERIC_IDENTITY_TYPE_HEADER)).thenReturn(VALID_IDENTITY_TYPE);
        when(request.getHeader(ERIC_IDENTITY_HEADER)).thenReturn(null);

        boolean result = authenticationInterceptor.preHandle(request, response, handler);

        assertFalse(result);
    }

    @ParameterizedTest
    @MethodSource("invalidPermissions")
    void preHandle_whenPermissionsIsInvalid_returns403(String permissions) {
        when(request.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);
        when(request.getHeader(ERIC_IDENTITY_TYPE_HEADER)).thenReturn(VALID_IDENTITY_TYPE);
        when(request.getHeader(ERIC_IDENTITY_HEADER)).thenReturn(VALID_API_KEY);
        when(request.getHeader(ERIC_PERMISSIONS_HEADER)).thenReturn(permissions);
        when(request.getHeader(ERIC_PARTNER_ORGANISATION_HEADER)).thenReturn(VALID_PARTNER_ORG);

        boolean result = authenticationInterceptor.preHandle(request, response, handler);

        assertFalse(result);
    }

    @ParameterizedTest
    @MethodSource("validPermissions")
    void preHandle_whenPermissionsIsValid_allowsRequest(String permissions) {
        when(request.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);
        when(request.getHeader(ERIC_IDENTITY_TYPE_HEADER)).thenReturn(VALID_IDENTITY_TYPE);
        when(request.getHeader(ERIC_IDENTITY_HEADER)).thenReturn(VALID_API_KEY);
        when(request.getHeader(ERIC_PERMISSIONS_HEADER)).thenReturn(permissions);
        when(request.getHeader(ERIC_PARTNER_ORGANISATION_HEADER)).thenReturn(VALID_PARTNER_ORG);

        boolean result = authenticationInterceptor.preHandle(request, response, handler);

        assertTrue(result);
    }

    @ParameterizedTest
    @MethodSource("missingPartnerOrganisation")
    void preHandle_whenPartnerOrganisationIsInvalid_returns403(String partnerOrg) {
        when(request.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);
        when(request.getHeader(ERIC_IDENTITY_TYPE_HEADER)).thenReturn(VALID_IDENTITY_TYPE);
        when(request.getHeader(ERIC_IDENTITY_HEADER)).thenReturn(VALID_API_KEY);
        when(request.getHeader(ERIC_PERMISSIONS_HEADER)).thenReturn(VALID_PERMISSION);
        when(request.getHeader(ERIC_PARTNER_ORGANISATION_HEADER)).thenReturn(partnerOrg);

        boolean result = authenticationInterceptor.preHandle(request, response, handler);

        assertFalse(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"HMRC", "companies-house", "any-valid-partner"})
    void preHandle_whenPartnerOrganisationIsValid_allowsRequest(String partnerOrg) {
        when(request.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);
        when(request.getHeader(ERIC_IDENTITY_TYPE_HEADER)).thenReturn(VALID_IDENTITY_TYPE);
        when(request.getHeader(ERIC_IDENTITY_HEADER)).thenReturn(VALID_API_KEY);
        when(request.getHeader(ERIC_PERMISSIONS_HEADER)).thenReturn(VALID_PERMISSION);
        when(request.getHeader(ERIC_PARTNER_ORGANISATION_HEADER)).thenReturn(partnerOrg);

        boolean result = authenticationInterceptor.preHandle(request, response, handler);

        assertTrue(result);
    }

    private void setupValidHeaders() {
        when(request.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);
        when(request.getHeader(ERIC_IDENTITY_TYPE_HEADER)).thenReturn(VALID_IDENTITY_TYPE);
        when(request.getHeader(ERIC_IDENTITY_HEADER)).thenReturn(VALID_API_KEY);
        when(request.getHeader(ERIC_PERMISSIONS_HEADER)).thenReturn(VALID_PERMISSION);
        when(request.getHeader(ERIC_PARTNER_ORGANISATION_HEADER)).thenReturn(VALID_PARTNER_ORG);
    }

    static Stream<String> invalidIdentityTypes() {
        return Stream.of(null, "", "   ", "invalid", "oauth2", "stream-key");
    }

    static Stream<String> invalidPermissions() {
        return Stream.of(null, "", "   ", "other-permission", "strike-off-partner-objections-extra");
    }

    static Stream<String> validPermissions() {
        return Stream.of("strike-off-partner-objections", "other-permission strike-off-partner-objections", "strike-off-partner-objections other-permission", "first second strike-off-partner-objections third");
    }

    static Stream<String> missingPartnerOrganisation() {
        return Stream.of(null, "", "   ", "");
    }
}
