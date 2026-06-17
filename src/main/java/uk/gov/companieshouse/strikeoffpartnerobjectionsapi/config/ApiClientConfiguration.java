package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.companieshouse.api.ApiClient;
import uk.gov.companieshouse.api.http.ApiKeyHttpClient;

@Configuration
public class ApiClientConfiguration {

    @Value("${CHS_API_KEY}")
    private String apiKey;

    @Value("${API_URL}")
    private String apiUrl;

    @Bean
    public ApiClient apiClient() {
        ApiKeyHttpClient httpClient = new ApiKeyHttpClient(apiKey);
        ApiClient client = new ApiClient(httpClient);
        client.setBasePath(apiUrl);
        return client;
    }
}

