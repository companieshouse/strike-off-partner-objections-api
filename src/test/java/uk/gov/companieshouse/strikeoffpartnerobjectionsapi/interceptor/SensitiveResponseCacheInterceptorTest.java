package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.interceptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@Tag("unit-test")
class SensitiveResponseCacheInterceptorTest {

    private final SensitiveResponseCacheInterceptor interceptor = new SensitiveResponseCacheInterceptor();

    @Test
    void preHandle_whenInvoked_setsHeadersAndContinuesProcessing() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(new MockHttpServletRequest(), response, new Object()));

        assertEquals("no-store", response.getHeader(HttpHeaders.CACHE_CONTROL));
        assertEquals("no-cache", response.getHeader(HttpHeaders.PRAGMA));
    }

    @Test
    void preHandle_whenHeadersExist_replacesCacheDirectivesAndPreservesResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.addHeader(HttpHeaders.CACHE_CONTROL, "public");
        response.addHeader(HttpHeaders.CACHE_CONTROL, "max-age=3600");
        response.addHeader(HttpHeaders.PRAGMA, "cache");
        response.setHeader("X-Request-Id", "request-123");
        response.setStatus(HttpStatus.CONFLICT.value());
        response.getWriter().write("existing response");
        MockHttpServletRequest request = new MockHttpServletRequest();
        Object handler = new Object();

        interceptor.preHandle(request, response, handler);
        interceptor.preHandle(request, response, handler);

        assertEquals(List.of("no-store"), List.copyOf(response.getHeaders(HttpHeaders.CACHE_CONTROL)));
        assertEquals(List.of("no-cache"), List.copyOf(response.getHeaders(HttpHeaders.PRAGMA)));
        assertEquals("request-123", response.getHeader("X-Request-Id"));
        assertEquals(HttpStatus.CONFLICT.value(), response.getStatus());
        assertEquals("existing response", response.getContentAsString());
    }
}
