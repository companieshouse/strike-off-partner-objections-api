package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.stream.Stream;

@Tag("unit-test")
class AuthenticationServiceTest {

    private static final String PERMISSIONS_HEADER = "ERIC-Authorised-Application-Permissions";
    private static final String REQUIRED_PERMISSION = "strike-off-partner-objections";

    @Mock
    private HttpServletRequest request;

    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        authenticationService = new AuthenticationService();
    }

    // ===== hasRequiredPermission =====

    @ParameterizedTest
    @MethodSource("differentValidPermissionCases")
    void hasRequiredPermission_whenPermissionIsValid_returnsTrue(String permission) {
        when(request.getHeader(PERMISSIONS_HEADER)).thenReturn(permission);

        assertThat(authenticationService.hasRequiredPermission(request)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("differentInvalidPermissionCases")
    void hasRequiredPermission_whenPermissionIsAbsentOrInvalid_returnsFalse(String permission) {
        when(request.getHeader(PERMISSIONS_HEADER)).thenReturn(permission);

        assertThat(authenticationService.hasRequiredPermission(request)).isFalse();
    }

    private static Stream<Arguments> differentValidPermissionCases() {
        return Stream.of(
                Arguments.of(REQUIRED_PERMISSION),
                Arguments.of("some-other-permission " + REQUIRED_PERMISSION + " another-permission"),
                Arguments.of("  " + REQUIRED_PERMISSION + "  ")
        );
    }

    private static Stream<Arguments> differentInvalidPermissionCases() {
        return Stream.of(
                Arguments.of("some-other-permission another-permission"),
                Arguments.of((Object) null),
                Arguments.of("", "   "),
                Arguments.of("strike-off-partner-objections-extra")
        );
    }
}


