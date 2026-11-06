package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.ERIC_PARTNER_ORGANISATION_HEADER;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.REQUIRED_ERIC_PERMISSION;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.companieshouse.api.InternalApiClient;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.interceptor.AuthenticationInterceptor;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.interceptor.InternalUserInterceptor;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.interceptor.SensitiveResponseCacheInterceptor;

@Tag("unit-test")
@WebMvcTest(controllers = SensitiveResponseCacheMvcTest.ProbeController.class)
@Import({ApplicationConfig.class, AuthenticationInterceptor.class, InternalUserInterceptor.class,
        SensitiveResponseCacheInterceptor.class, SensitiveResponseCacheMvcTest.ProbeController.class})
class SensitiveResponseCacheMvcTest {

    private static final String OBJECTIONS_PATH = "/company/12345678/strike-off-partner-objections";
    private static final String WITHDRAWALS_PATH = "/company/12345678/strike-off-partner-objections-withdrawals";
    private static final String INTERNAL_OBJECTIONS_PATH = "/internal" + OBJECTIONS_PATH;
    private static final String INTERNAL_WITHDRAWALS_PATH = "/internal" + WITHDRAWALS_PATH;
    private static final String PERMISSIONS_HEADER = "ERIC-Authorised-Application-Permissions";
    private static final String IDENTITY_HEADER = "ERIC-Identity";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InternalApiClient internalApiClient;

    @ParameterizedTest
    @ValueSource(strings = {
            OBJECTIONS_PATH, OBJECTIONS_PATH + "/", OBJECTIONS_PATH + "/objection-123",
            OBJECTIONS_PATH + "/future/case-details", WITHDRAWALS_PATH,
            WITHDRAWALS_PATH + "/withdrawal-123", WITHDRAWALS_PATH + "/future/case-details",
            INTERNAL_OBJECTIONS_PATH, INTERNAL_OBJECTIONS_PATH + "/objection-123/status",
            INTERNAL_WITHDRAWALS_PATH, INTERNAL_WITHDRAWALS_PATH + "/withdrawal-123/withdrawal-status"
    })
    void protectedRoute_whenAuthenticated_setsCacheHeadersWithoutChangingResponse(String path) throws Exception {
        mockMvc.perform(authenticatedRequest(path).header("ERIC-Authorised-Key-Privileges", "internal-app"))
                .andExpect(status().isOk())
                .andExpect(content().string("unchanged"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/healthcheck", "/strike-off-partner-objections-api/healthcheck",
            "/strike-off-partner-objections-api/healthcheck/readiness",
            "/strike-off-partner-objections-api/healthcheck/liveness",
            "/actuator/metrics", "/status", "/internal/status",
            "/company/12345678/profile", "/company/12345678/strike-off-partner-objections-summary",
            "/company/12345678/strike-off-partner-objections-withdrawals-summary"
    })
    void unrelatedRoute_whenHandled_doesNotReceiveCachePolicy(String path) throws Exception {
        mockMvc.perform(authenticatedRequest(path).header("ERIC-Authorised-Key-Privileges", "internal-app"))
                .andExpect(status().isOk())
                .andExpect(content().string("unchanged"))
                .andExpect(header().doesNotExist(HttpHeaders.CACHE_CONTROL))
                .andExpect(header().doesNotExist(HttpHeaders.PRAGMA));
    }

    @ParameterizedTest
    @ValueSource(strings = {OBJECTIONS_PATH, WITHDRAWALS_PATH, INTERNAL_OBJECTIONS_PATH, INTERNAL_WITHDRAWALS_PATH})
    void protectedRoute_whenIdentityTypeIsMissing_preservesForbiddenResponseWithCacheHeaders(String path)
            throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Missing or invalid ERIC-Identity-Type header"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"));
    }

    @ParameterizedTest
    @ValueSource(strings = {OBJECTIONS_PATH, WITHDRAWALS_PATH})
    void protectedRoute_whenIdentityIsMissing_preservesUnauthorisedResponseWithCacheHeaders(String path)
            throws Exception {
        mockMvc.perform(get(path).header("ERIC-Identity-Type", "key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid API key"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"));
    }

    @ParameterizedTest
    @ValueSource(strings = {OBJECTIONS_PATH, WITHDRAWALS_PATH})
    void protectedRoute_whenPermissionIsMissing_preservesForbiddenResponseWithCacheHeaders(String path)
            throws Exception {
        mockMvc.perform(get(path).header("ERIC-Identity-Type", "key").header(IDENTITY_HEADER, "test-key"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Missing required permission: " + REQUIRED_ERIC_PERMISSION))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"));
    }

    @ParameterizedTest
    @ValueSource(strings = {OBJECTIONS_PATH, WITHDRAWALS_PATH})
    void protectedRoute_whenPartnerIsMissing_preservesForbiddenResponseWithCacheHeaders(String path)
            throws Exception {
        mockMvc.perform(get(path).header("ERIC-Identity-Type", "key")
                        .header(IDENTITY_HEADER, "test-key")
                        .header(PERMISSIONS_HEADER, REQUIRED_ERIC_PERMISSION))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Missing required header: " + ERIC_PARTNER_ORGANISATION_HEADER))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"));
    }

    @ParameterizedTest
    @ValueSource(strings = {INTERNAL_OBJECTIONS_PATH + "/id/status",
            INTERNAL_WITHDRAWALS_PATH + "/id/withdrawal-status"})
    void internalRoute_whenPrivilegeIsMissing_preservesBodylessForbiddenWithCacheHeaders(String path)
            throws Exception {
        mockMvc.perform(authenticatedRequest(path))
                .andExpect(status().isForbidden())
                .andExpect(content().string(""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"));
    }

    private MockHttpServletRequestBuilder authenticatedRequest(String path) {
        return get(path)
                .header("ERIC-Identity-Type", "key")
                .header(IDENTITY_HEADER, "test-key")
                .header(PERMISSIONS_HEADER, REQUIRED_ERIC_PERMISSION)
                .header(ERIC_PARTNER_ORGANISATION_HEADER, "hmrc");
    }

    @RestController
    static class ProbeController {

        @GetMapping("/**")
        public String respond() {
            return "unchanged";
        }
    }
}
