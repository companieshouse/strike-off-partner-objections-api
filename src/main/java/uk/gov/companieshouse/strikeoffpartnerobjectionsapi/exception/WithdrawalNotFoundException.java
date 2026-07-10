package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception;

public class WithdrawalNotFoundException extends RuntimeException {
    public WithdrawalNotFoundException(String message) {
        super(message);
    }
}
