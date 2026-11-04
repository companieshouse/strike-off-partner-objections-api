package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config.EmailProperties;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config.HmrcOAuthProperties;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.EmailValidationException;

/**
 * Service for validating email-related information in requests.
 * Validates that email address is from a trusted source.
 * Throws EmailValidationException if validation fails.
 */
@Service
@EnableConfigurationProperties({HmrcOAuthProperties.class, EmailProperties.class})
public class EmailValidator {

    private final EmailProperties emailProperties;

    public EmailValidator(EmailProperties emailProperties) {
        this.emailProperties = emailProperties;
    }

    public void validateEmail(String email) {
        String domain = email.substring(email.lastIndexOf('@') + 1);

        boolean recognised = emailProperties.allowedDomains().stream().anyMatch(domain::equalsIgnoreCase);

        if (recognised) {
            return;
        }

        // If this line is reached, the email was not from a recognised source
        LOGGER.error("Email is not from a recognised source");
        throw new EmailValidationException("Email is not from a recognised source", "EMAIL_NOT_RECOGNISED");
    }

}