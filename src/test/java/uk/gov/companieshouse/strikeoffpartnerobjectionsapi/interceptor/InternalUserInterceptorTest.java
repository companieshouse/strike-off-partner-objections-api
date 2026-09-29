package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.ERIC_AUTHORISED_KEY_PRIVILEGES;

/**
 * Unit tests for the InternalUserInterceptor.
 *
 * <p>Tests the interceptor's validation of the internal-app privilege in the
 * ERIC-Authorised-Key-Privileges header for internal endpoint access.</p>
 */
class InternalUserInterceptorTest {

    private InternalUserInterceptor interceptor;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unused")
        var mockitoCloseable = MockitoAnnotations.openMocks(this);
        try (mockitoCloseable) {
            interceptor = new InternalUserInterceptor();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "internal-app",
            "internal-app,payment",
            "payment, internal-app, sensitive-data"
    })
    void preHandle_WithInternalAppPrivilege_AllowsRequest(String privileges) {
        when(request.getHeader(ERIC_AUTHORISED_KEY_PRIVILEGES)).thenReturn(privileges);

        boolean result = interceptor.preHandle(request, response, null);

        assertTrue(result, "Request with internal-app privilege should be allowed");
    }

    @ParameterizedTest
    @MethodSource("invalidPrivilegesProvider")
    void preHandle_WithoutInternalAppPrivilege_ReturnsForbidden(String privileges) {
        when(request.getHeader(ERIC_AUTHORISED_KEY_PRIVILEGES)).thenReturn(privileges);

        boolean result = interceptor.preHandle(request, response, null);

        assertFalse(result, "Request without internal-app privilege should be rejected");
        verify(response).setStatus(HttpStatus.FORBIDDEN.value());
    }

    private static Stream<String> invalidPrivilegesProvider() {
        return Stream.of(
                "payment",
                null,
                "",
                "   ",
                "internal",
                "sensitive-data",
                "user-data",
                "payment,sensitive-data"
        );
    }
}
