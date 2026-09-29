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
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.interceptor.InternalUserInterceptor;

@Tag("unit-test")
class ApplicationConfigTest {

    @Test
    void internalApiClient_whenInvoked_createsBean() {
        ApplicationConfig config = new ApplicationConfig(null, null);

        try {
            InternalApiClient apiClient = config.internalApiClient();
            assertNotNull(apiClient);
        } catch (EnvironmentVariableException exception) {
            assertTrue(exception.getMessage().contains("CHS_API_KEY"));
        }
    }

    @Test
    void addInterceptors_whenInvoked_registersAuthenticationInterceptorForAllPaths() {
        AuthenticationInterceptor authInterceptor = Mockito.mock(AuthenticationInterceptor.class);
        InterceptorRegistry registry = Mockito.mock(InterceptorRegistry.class);
        InterceptorRegistration registration = Mockito.mock(InterceptorRegistration.class);
        ApplicationConfig config = new ApplicationConfig(authInterceptor, null);

        when(registry.addInterceptor(authInterceptor)).thenReturn(registration);
        when(registration.addPathPatterns("/**")).thenReturn(registration);
        when(registration.excludePathPatterns("/healthcheck")).thenReturn(registration);

        config.addInterceptors(registry);

        verify(registry).addInterceptor(authInterceptor);
        verify(registration).addPathPatterns("/**");
        verify(registration).excludePathPatterns("/healthcheck");
    }

    @Test
    void addInterceptors_whenAuthenticationInterceptorIsNull_doesNotRegisterAuthInterceptor() {
        ApplicationConfig config = new ApplicationConfig(null, null);
        InterceptorRegistry registry = Mockito.mock(InterceptorRegistry.class);

        config.addInterceptors(registry);

        verifyNoInteractions(registry);
    }

    @Test
    void addInterceptors_whenInvoked_registersInternalUserInterceptorForInternalPaths() {
        InternalUserInterceptor internalInterceptor = Mockito.mock(InternalUserInterceptor.class);
        InterceptorRegistry registry = Mockito.mock(InterceptorRegistry.class);
        InterceptorRegistration registration = Mockito.mock(InterceptorRegistration.class);
        ApplicationConfig config = new ApplicationConfig(null, internalInterceptor);

        when(registry.addInterceptor(internalInterceptor)).thenReturn(registration);
        when(registration.addPathPatterns("/internal/**")).thenReturn(registration);

        config.addInterceptors(registry);

        verify(registry).addInterceptor(internalInterceptor);
        verify(registration).addPathPatterns("/internal/**");
    }

    @Test
    void addInterceptors_whenBothInterceptorsPresent_registersBoth() {
        AuthenticationInterceptor authInterceptor = Mockito.mock(AuthenticationInterceptor.class);
        InternalUserInterceptor internalInterceptor = Mockito.mock(InternalUserInterceptor.class);
        InterceptorRegistry registry = Mockito.mock(InterceptorRegistry.class);
        InterceptorRegistration authRegistration = Mockito.mock(InterceptorRegistration.class);
        InterceptorRegistration internalRegistration = Mockito.mock(InterceptorRegistration.class);
        ApplicationConfig config = new ApplicationConfig(authInterceptor, internalInterceptor);

        when(registry.addInterceptor(authInterceptor)).thenReturn(authRegistration);
        when(authRegistration.addPathPatterns("/**")).thenReturn(authRegistration);
        when(authRegistration.excludePathPatterns("/healthcheck")).thenReturn(authRegistration);
        when(registry.addInterceptor(internalInterceptor)).thenReturn(internalRegistration);
        when(internalRegistration.addPathPatterns("/internal/**")).thenReturn(internalRegistration);

        config.addInterceptors(registry);

        verify(registry).addInterceptor(authInterceptor);
        verify(registry).addInterceptor(internalInterceptor);
    }
}
