package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.EmailValidationException;

@Tag("unit-test")
@ExtendWith(MockitoExtension.class)
class EmailValidatorTest {

    private EmailValidator emailValidator;

    @BeforeEach
    void setUp() {
        emailValidator = new EmailValidator();
    }

    @Test
    void testValidateEmailWithUnrecognisedDomain() {
        String email = "test@companieshouse.gov.uk";
        assertThrows(EmailValidationException.class, () -> emailValidator.validateEmail(email));
    }

    @Test
    void testValidateEmailWithDomainNotAtEnd() {
        String email = "hmrc.gov.uk@somewhereelse.org";
        assertThrows(EmailValidationException.class, () -> emailValidator.validateEmail(email));
    }

    @Test
    void testValidateEmailWithDomainAtEndButStillInvalid() {
        String email = "test@fakehmrc.gov.uk";
        assertThrows(EmailValidationException.class, () -> emailValidator.validateEmail(email));
    }

    @Test
    void testValidateEmailWithRecognisedDomain() {
        String email = "test@hmrc.gov.uk";
        assertDoesNotThrow(() -> emailValidator.validateEmail(email));
    }
}