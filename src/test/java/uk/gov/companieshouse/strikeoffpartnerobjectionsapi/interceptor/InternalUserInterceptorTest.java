package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.interceptor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

@Tag("unit-test")
@ExtendWith(MockitoExtension.class)
class InternalUserInterceptorTest {

    private static final String X_REQUEST_ID_HEADER = "X-Request-Id";
    private static final String ERIC_INTERNAL_APP_PRIVILEGES = "ERIC-Authorised-Application-Privileges";
    private static final String REQUEST_ID = "test-request-id-456";

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private InternalUserInterceptor internalUserInterceptor;
    private Object handler;

    @BeforeEach
    void setUp() {
        internalUserInterceptor = new InternalUserInterceptor();
        handler = new Object();
    }

    @Test
    void preHandle_whenInternalAppPrivilegesIsTrueAndHeaderIsValid_allowsRequest() {
        setupValidInternalPrivileges();

        boolean result = internalUserInterceptor.preHandle(request, response, handler);

        assertTrue(result);
    }

    @Test
    void preHandle_whenInternalAppPrivilegesIsFalse_returns403() throws IOException {
        setupInternalPrivilegesFalse();
        stubResponseWriter();

        boolean result = internalUserInterceptor.preHandle(request, response, handler);

        assertFalse(result);
    }


    @ParameterizedTest
    @MethodSource("allInvalidPrivileges")
    void preHandle_whenPrivilegeHeaderIsInvalid_returns403(String privilegeHeader) throws IOException {
        when(request.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);
        when(request.getHeader(ERIC_INTERNAL_APP_PRIVILEGES)).thenReturn(privilegeHeader);
        stubResponseWriter();

        boolean result = internalUserInterceptor.preHandle(request, response, handler);

        assertFalse(result);
    }

    @ParameterizedTest
    @MethodSource("allValidPrivileges")
    void preHandle_whenPrivilegeHeaderIsValid_allowsRequest(String privilegeHeader) {
        when(request.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);
        when(request.getHeader(ERIC_INTERNAL_APP_PRIVILEGES)).thenReturn(privilegeHeader);

        boolean result = internalUserInterceptor.preHandle(request, response, handler);

        assertTrue(result);
    }

    @Test
    void preHandle_whenPrivilegeCheckFailsAndWriterThrowsIOException_returnsFalse() throws IOException {
        when(request.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);
        when(request.getHeader(ERIC_INTERNAL_APP_PRIVILEGES)).thenReturn(null);
        doThrow(new IOException("stream closed")).when(response).getWriter();

        boolean result = internalUserInterceptor.preHandle(request, response, handler);

        assertFalse(result);
    }


    private void setupValidInternalPrivileges() {
        when(request.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);
        when(request.getHeader(ERIC_INTERNAL_APP_PRIVILEGES))
                .thenReturn("{\"internal_app_privileges\":true}");
    }

    private void setupInternalPrivilegesFalse() throws IOException {
        when(request.getHeader(X_REQUEST_ID_HEADER)).thenReturn(REQUEST_ID);
        when(request.getHeader(ERIC_INTERNAL_APP_PRIVILEGES))
                .thenReturn("{\"internal_app_privileges\":false}");
        stubResponseWriter();
    }


    private void stubResponseWriter() throws IOException {
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
    }

    static Stream<String> missingPrivilegeHeaders() {
        return Stream.of(null, "", "   ");
    }


    static Stream<String> allInvalidPrivileges() {
        return Stream.of(
            // Missing/blank headers
            null, "", "   ",
            // Invalid JSON
            "{invalid json}", "{\"internal_app_privileges\":}", "not-json-at-all", "[1,2,3]",
            // Missing privilege flag
            "{}", "{\"other_field\":true}", "{\"another_field\":false}", "{\"internal_app_privileges_extra\":true}",
            // Invalid types
            "{\"internal_app_privileges\":\"true\"}", "{\"internal_app_privileges\":1}"
        );
    }

    static Stream<String> allValidPrivileges() {
        return Stream.of(
            "{\"internal_app_privileges\":true}",
            "  {\"internal_app_privileges\":true}  ",
            "{\"internal_app_privileges\":true,\"other_field\":\"value\"}",
            "{\"other_field\":\"value\",\"internal_app_privileges\":true}",
            "{\"internal_app_privileges\":true,\"nested\":{\"key\":\"value\"}}"
        );
    }
}








