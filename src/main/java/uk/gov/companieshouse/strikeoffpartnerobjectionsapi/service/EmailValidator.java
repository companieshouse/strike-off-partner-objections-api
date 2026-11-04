package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;

import org.springframework.stereotype.Service;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.EmailValidationException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.enums.AcceptedEmailAddresses;
import java.util.Arrays;

/**
 * Service for validating email-related information in requests.
 * Validates that email address is from a trusted source.
 * Throws EmailValidationException if validation fails.
 */
@Service
public class EmailValidator {

    public void validateEmail(String email) {
        String domain = email.substring(email.lastIndexOf('@') + 1);

        boolean recognised = Arrays.stream(AcceptedEmailAddresses.values())
                .map(AcceptedEmailAddresses::getDomain)
                .anyMatch(domain::equalsIgnoreCase);

        if (recognised) {
            return;
        }

        // If this line is reached, the email was not from a recognised source
        LOGGER.error("Email is not from a recognised source");
        throw new EmailValidationException("Email is not from a recognised source", "EMAIL_NOT_RECOGNISED");
    }

}