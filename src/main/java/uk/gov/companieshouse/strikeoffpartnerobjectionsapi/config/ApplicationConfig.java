package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config;

import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import uk.gov.companieshouse.api.InternalApiClient;
import uk.gov.companieshouse.sdk.manager.ApiSdkManager;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.interceptor.AuthenticationInterceptor;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.interceptor.InternalUserInterceptor;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.interceptor.SensitiveResponseCacheInterceptor;

@Configuration
@EnableConfigurationProperties(HmrcOAuthProperties.class)
public class ApplicationConfig implements WebMvcConfigurer {

    private static final int CACHE_POLICY_ORDER = 0;
    private static final String[] SENSITIVE_PATHS = {
            "/company/{company_number}/strike-off-partner-objections",
            "/company/{company_number}/strike-off-partner-objections/**",
            "/company/{company_number}/strike-off-partner-objections-withdrawals",
            "/company/{company_number}/strike-off-partner-objections-withdrawals/**",
            "/internal/company/{company_number}/strike-off-partner-objections",
            "/internal/company/{company_number}/strike-off-partner-objections/**",
            "/internal/company/{company_number}/strike-off-partner-objections-withdrawals",
            "/internal/company/{company_number}/strike-off-partner-objections-withdrawals/**"
    };

    private final AuthenticationInterceptor authenticationInterceptor;
    private final InternalUserInterceptor internalUserInterceptor;
    private final SensitiveResponseCacheInterceptor sensitiveResponseCacheInterceptor;

    public ApplicationConfig(
            AuthenticationInterceptor authenticationInterceptor,
            InternalUserInterceptor internalUserInterceptor,
            SensitiveResponseCacheInterceptor sensitiveResponseCacheInterceptor) {
        this.authenticationInterceptor = authenticationInterceptor;
        this.internalUserInterceptor = internalUserInterceptor;
        this.sensitiveResponseCacheInterceptor = sensitiveResponseCacheInterceptor;
    }

    @Bean
    public InternalApiClient internalApiClient() {
        return ApiSdkManager.getPrivateSDK();
    }

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000); // 5 seconds
        requestFactory.setReadTimeout(10000); // 10 seconds
        return new RestTemplate(requestFactory);
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(sensitiveResponseCacheInterceptor)
                .addPathPatterns(SENSITIVE_PATHS).order(CACHE_POLICY_ORDER);
        registry.addInterceptor(authenticationInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/healthcheck").order(1);
        registry.addInterceptor(internalUserInterceptor)
                .addPathPatterns("/internal/**").order(2);
    }
}
