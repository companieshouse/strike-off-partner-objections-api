package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config;

import java.util.List;
import org.springframework.boot.actuate.endpoint.web.EndpointMediaTypes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

/**
 * Configuration for management endpoints.
 * This class prevents the health check endpoint returning technology specific content types
 * and instead returns only application/json.
 */
@Configuration(proxyBeanMethods = false)
public class ManagementEndpointConfiguration {

    private static final List<String> JSON_MEDIA_TYPES = List.of(MediaType.APPLICATION_JSON_VALUE);

    @Bean
    EndpointMediaTypes endpointMediaTypes() {
        return new EndpointMediaTypes(JSON_MEDIA_TYPES, JSON_MEDIA_TYPES);
    }
}

