package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import uk.gov.companieshouse.api.InternalApiClient;
import uk.gov.companieshouse.sdk.manager.ApiSdkManager;

@Configuration
public class ApplicationConfig implements WebMvcConfigurer {

    @Bean
    @Primary
    public InternalApiClient internalApiClient() {
        return ApiSdkManager.getPrivateSDK();
    }

}
