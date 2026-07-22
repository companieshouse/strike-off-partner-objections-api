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
class TemporaryAuthenticationHeaderExtractorTest {

    private static final String PERMISSIONS_HEADER = "ERIC-Authorised-Application-Permissions";
    private static final String PARTNER_ORG_HEADER = "ERIC-Authorised-Application-Partner-Organisation";
    private static final String REQUIRED_PERMISSION = "strike-off-partner-objections";

    @Mock
    private HttpServletRequest request;

    private TemporaryAuthenticationHeaderExtractor temporaryAuthenticationHeaderExtractor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        temporaryAuthenticationHeaderExtractor = new TemporaryAuthenticationHeaderExtractor(request);
    }

    // ===== hasRequiredPermission =====

    @ParameterizedTest
    @MethodSource("differentValidPermissionCases")
    void hasRequiredPermission_whenPermissionIsValid_returnsTrue(String permission) {
        when(request.getHeader(PERMISSIONS_HEADER)).thenReturn(permission);

        assertThat(temporaryAuthenticationHeaderExtractor.hasRequiredPermission()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("differentInvalidPermissionCases")
    void hasRequiredPermission_whenPermissionIsAbsentOrInvalid_returnsFalse(String permission) {
        when(request.getHeader(PERMISSIONS_HEADER)).thenReturn(permission);

        assertThat(temporaryAuthenticationHeaderExtractor.hasRequiredPermission()).isFalse();
    }

    // ===== getPartnerOrganisation =====

    @ParameterizedTest
    @MethodSource("differentValidPartnerOrgCases")
    void getPartnerOrganisation_whenHeaderIsValid_returnsPartnerOrg(String partnerOrg) {
        when(request.getHeader(PARTNER_ORG_HEADER)).thenReturn(partnerOrg);

        assertThat(temporaryAuthenticationHeaderExtractor.getPartnerOrganisation())
                .isNotNull()
                .isNotBlank();
    }

    @ParameterizedTest
    @MethodSource("differentInvalidPartnerOrgCases")
    void getPartnerOrganisation_whenHeaderIsAbsentOrBlank_returnsNull(String partnerOrg) {
        when(request.getHeader(PARTNER_ORG_HEADER)).thenReturn(partnerOrg);

        assertThat(temporaryAuthenticationHeaderExtractor.getPartnerOrganisation()).isNull();
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
                Arguments.of("   "),
                Arguments.of("strike-off-partner-objections-extra")
        );
    }

    private static Stream<Arguments> differentValidPartnerOrgCases() {
        return Stream.of(
                Arguments.of("HMRC"),
                Arguments.of("  HMRC  "),
                Arguments.of("Companies House")
        );
    }

    private static Stream<Arguments> differentInvalidPartnerOrgCases() {
        return Stream.of(
                Arguments.of((Object) null),
                Arguments.of("   ")
        );
    }
}
