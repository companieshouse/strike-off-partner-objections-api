package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import uk.gov.companieshouse.api.InternalApiClient;
import uk.gov.companieshouse.environment.exception.EnvironmentVariableException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.interceptor.AuthenticationInterceptor;

@Tag("unit-test")
class ApplicationConfigTest {

    @Test
    void internalApiClient_whenInvoked_createsBean() {
        ApplicationConfig config = new ApplicationConfig(null);

        try {
            InternalApiClient apiClient = config.internalApiClient();
            assertNotNull(apiClient);
        } catch (EnvironmentVariableException exception) {
            assertTrue(exception.getMessage().contains("CHS_API_KEY"));
        }
    }

    @Test
    void addInterceptors_whenInvoked_registersAuthenticationInterceptorForAllPaths() {
        AuthenticationInterceptor interceptor = Mockito.mock(AuthenticationInterceptor.class);
        InterceptorRegistry registry = Mockito.mock(InterceptorRegistry.class);
        InterceptorRegistration registration = Mockito.mock(InterceptorRegistration.class);
        ApplicationConfig config = new ApplicationConfig(interceptor);

        when(registry.addInterceptor(interceptor)).thenReturn(registration);
        when(registration.addPathPatterns("/**")).thenReturn(registration);

        config.addInterceptors(registry);

        verify(registry).addInterceptor(interceptor);
        verify(registration).addPathPatterns("/**");
    }

    @Test
    void addInterceptors_whenAuthenticationInterceptorIsNull_doesNothing() {
        ApplicationConfig config = new ApplicationConfig(null);
        InterceptorRegistry registry = Mockito.mock(InterceptorRegistry.class);

        config.addInterceptors(registry);

        verifyNoInteractions(registry);
    }
}
