package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config.EmailProperties;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.EmailValidationException;

@Tag("unit-test")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = EmailValidatorTest.TestConfig.class)
@TestPropertySource(locations = "classpath:application.properties")
class EmailValidatorTest {

    @Autowired
    private EmailValidator emailValidator;

    @ParameterizedTest
    @ValueSource(strings = {
            "test@companieshouse.gov.uk",
            "hmrc.gov.uk@somewhereelse.org",
            "test@fakehmrc.gov.uk"
    })
    void testValidateEmailWithUnrecognisedDomain(String email) {
        assertThrows(EmailValidationException.class, () -> emailValidator.validateEmail(email));
    }

    @Test
    void testValidateEmailWithRecognisedDomain() {
        assertDoesNotThrow(() -> emailValidator.validateEmail("test@hmrc.gov.uk"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(EmailProperties.class)
    @Import(EmailValidator.class)
    static class TestConfig {
    }
}