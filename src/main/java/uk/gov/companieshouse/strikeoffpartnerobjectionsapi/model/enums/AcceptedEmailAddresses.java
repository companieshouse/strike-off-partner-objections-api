package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.enums;

public enum AcceptedEmailAddresses {

    HMRC("hmrc.gov.uk");

    private final String domain;

    AcceptedEmailAddresses(String domain) {
        this.domain = domain;
    }

    public String getDomain() {
        return domain;
    }
}
