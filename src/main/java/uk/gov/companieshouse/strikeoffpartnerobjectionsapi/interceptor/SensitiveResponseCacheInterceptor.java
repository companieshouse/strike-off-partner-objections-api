package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Prevents storage of sensitive responses before authentication or controller processing.
 */
@Component
public class SensitiveResponseCacheInterceptor implements HandlerInterceptor {

    private static final String NO_STORE = "no-store";
    private static final String NO_CACHE = "no-cache";

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull Object handler) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE);
        response.setHeader(HttpHeaders.PRAGMA, NO_CACHE);
        return true;
    }
}
