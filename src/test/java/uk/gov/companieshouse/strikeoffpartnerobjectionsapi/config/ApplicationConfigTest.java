package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import uk.gov.companieshouse.api.InternalApiClient;
import uk.gov.companieshouse.environment.exception.EnvironmentVariableException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.interceptor.AuthenticationInterceptor;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.interceptor.InternalUserInterceptor;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.interceptor.SensitiveResponseCacheInterceptor;

@Tag("unit-test")
class ApplicationConfigTest {

    private final SensitiveResponseCacheInterceptor cacheInterceptor = new SensitiveResponseCacheInterceptor();

    @Test
    void internalApiClient_whenInvoked_createsBean() {
        ApplicationConfig config = new ApplicationConfig(
                Mockito.mock(AuthenticationInterceptor.class),
                Mockito.mock(InternalUserInterceptor.class),
                cacheInterceptor);

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
        InternalUserInterceptor internalInterceptor = Mockito.mock(InternalUserInterceptor.class);
        InterceptorRegistry registry = Mockito.mock(InterceptorRegistry.class);
        InterceptorRegistration authRegistration = Mockito.mock(InterceptorRegistration.class);
        InterceptorRegistration internalRegistration = Mockito.mock(InterceptorRegistration.class);
        ApplicationConfig config = new ApplicationConfig(authInterceptor, internalInterceptor, cacheInterceptor);
        InterceptorRegistration cacheRegistration = registerCacheInterceptor(registry);

        when(registry.addInterceptor(authInterceptor)).thenReturn(authRegistration);
        when(authRegistration.addPathPatterns("/**")).thenReturn(authRegistration);
        when(authRegistration.excludePathPatterns("/healthcheck")).thenReturn(authRegistration);
        when(registry.addInterceptor(internalInterceptor)).thenReturn(internalRegistration);
        when(internalRegistration.addPathPatterns("/internal/**")).thenReturn(internalRegistration);

        config.addInterceptors(registry);

        verify(registry).addInterceptor(authInterceptor);
        verify(authRegistration).addPathPatterns("/**");
        verify(authRegistration).excludePathPatterns("/healthcheck");
        verify(registry).addInterceptor(internalInterceptor);
        verify(cacheRegistration).order(0);
    }

    @Test
    void addInterceptors_whenInvoked_registersInternalUserInterceptorForInternalPaths() {
        InternalUserInterceptor internalInterceptor = Mockito.mock(InternalUserInterceptor.class);
        AuthenticationInterceptor authInterceptor = Mockito.mock(AuthenticationInterceptor.class);
        InterceptorRegistry registry = Mockito.mock(InterceptorRegistry.class);
        InterceptorRegistration authRegistration = Mockito.mock(InterceptorRegistration.class);
        InterceptorRegistration internalRegistration = Mockito.mock(InterceptorRegistration.class);
        ApplicationConfig config = new ApplicationConfig(authInterceptor, internalInterceptor, cacheInterceptor);
        registerCacheInterceptor(registry);

        when(registry.addInterceptor(authInterceptor)).thenReturn(authRegistration);
        when(authRegistration.addPathPatterns("/**")).thenReturn(authRegistration);
        when(authRegistration.excludePathPatterns("/healthcheck")).thenReturn(authRegistration);
        when(registry.addInterceptor(internalInterceptor)).thenReturn(internalRegistration);
        when(internalRegistration.addPathPatterns("/internal/**")).thenReturn(internalRegistration);

        config.addInterceptors(registry);

        verify(registry).addInterceptor(authInterceptor);
        verify(registry).addInterceptor(internalInterceptor);
        verify(internalRegistration).addPathPatterns("/internal/**");
    }

    @Test
    void addInterceptors_whenBothInterceptorsPresent_registersBoth() {
        AuthenticationInterceptor authInterceptor = Mockito.mock(AuthenticationInterceptor.class);
        InternalUserInterceptor internalInterceptor = Mockito.mock(InternalUserInterceptor.class);
        InterceptorRegistry registry = Mockito.mock(InterceptorRegistry.class);
        InterceptorRegistration authRegistration = Mockito.mock(InterceptorRegistration.class);
        InterceptorRegistration internalRegistration = Mockito.mock(InterceptorRegistration.class);
        ApplicationConfig config = new ApplicationConfig(authInterceptor, internalInterceptor, cacheInterceptor);
        InterceptorRegistration cacheRegistration = registerCacheInterceptor(registry);

        when(registry.addInterceptor(authInterceptor)).thenReturn(authRegistration);
        when(authRegistration.addPathPatterns("/**")).thenReturn(authRegistration);
        when(authRegistration.excludePathPatterns("/healthcheck")).thenReturn(authRegistration);
        when(registry.addInterceptor(internalInterceptor)).thenReturn(internalRegistration);
        when(internalRegistration.addPathPatterns("/internal/**")).thenReturn(internalRegistration);

        config.addInterceptors(registry);

        verify(registry).addInterceptor(authInterceptor);
        verify(registry).addInterceptor(internalInterceptor);
        verify(cacheRegistration).order(0);
        verify(authRegistration).order(1);
        verify(internalRegistration).order(2);
    }

    @Test
    void applicationContext_whenCacheInterceptorIsMissing_failsToStart() {
        new ApplicationContextRunner()
                .withUserConfiguration(ApplicationConfig.class)
                .withBean(AuthenticationInterceptor.class, () -> Mockito.mock(AuthenticationInterceptor.class))
                .withBean(InternalUserInterceptor.class, () -> Mockito.mock(InternalUserInterceptor.class))
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(NoSuchBeanDefinitionException.class)
                        .hasStackTraceContaining(SensitiveResponseCacheInterceptor.class.getName()));
    }

    private InterceptorRegistration registerCacheInterceptor(InterceptorRegistry registry) {
        InterceptorRegistration registration = Mockito.mock(InterceptorRegistration.class);
        Mockito.when(registry.addInterceptor(cacheInterceptor)).thenReturn(registration);
        Mockito.when(registration.addPathPatterns(Mockito.any(String[].class))).thenReturn(registration);
        return registration;
    }
}
