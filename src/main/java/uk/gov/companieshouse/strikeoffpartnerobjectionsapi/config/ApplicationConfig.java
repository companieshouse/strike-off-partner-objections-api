package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import uk.gov.companieshouse.api.InternalApiClient;
import uk.gov.companieshouse.sdk.manager.ApiSdkManager;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.interceptor.AuthenticationInterceptor;

@Configuration
public class ApplicationConfig implements WebMvcConfigurer {

    private final AuthenticationInterceptor authenticationInterceptor;

    public ApplicationConfig(@Autowired(required = false) AuthenticationInterceptor authenticationInterceptor) {
        this.authenticationInterceptor = authenticationInterceptor;
    }

    @Bean
    public InternalApiClient internalApiClient() {
        return ApiSdkManager.getPrivateSDK();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (authenticationInterceptor != null) {
            registry.addInterceptor(authenticationInterceptor)
                    .addPathPatterns("/**")
                    .excludePathPatterns("/healthcheck");
        }
    }
}
