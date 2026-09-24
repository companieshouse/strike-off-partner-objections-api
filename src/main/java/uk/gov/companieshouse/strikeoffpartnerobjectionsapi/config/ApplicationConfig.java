package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import uk.gov.companieshouse.api.InternalApiClient;
import uk.gov.companieshouse.sdk.manager.ApiSdkManager;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.interceptor.AuthenticationInterceptor;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.interceptor.InternalUserInterceptor;

@Configuration
public class ApplicationConfig implements WebMvcConfigurer {

    private final AuthenticationInterceptor authenticationInterceptor;
    private final InternalUserInterceptor internalUserInterceptor;

    public ApplicationConfig(
            @Autowired(required = false) AuthenticationInterceptor authenticationInterceptor,
            @Autowired(required = false) InternalUserInterceptor internalUserInterceptor) {
        this.authenticationInterceptor = authenticationInterceptor;
        this.internalUserInterceptor = internalUserInterceptor;
    }

    @Bean
    public InternalApiClient internalApiClient() {
        return ApiSdkManager.getPrivateSDK();
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        if (authenticationInterceptor != null) {
            registry.addInterceptor(authenticationInterceptor)
                    .addPathPatterns("/**")
                    .excludePathPatterns("/healthcheck");
        }

        if (internalUserInterceptor != null) {
            registry.addInterceptor(internalUserInterceptor)
                    .addPathPatterns("/internal/company/*/strike-off-partner-objections/*/status")
                    .addPathPatterns("/internal/company/*/strike-off-partner-objections-withdrawals/*/withdrawal-status");
        }
    }
}
