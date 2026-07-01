package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

abstract class AbstractPartnerLinksContractTest<T extends PartnerLinks> {

    private T links;

    protected abstract T createLinks();

    protected abstract String firstSelfUrl();

    protected abstract String secondSelfUrl();

    @BeforeEach
    void setUpLinks() {
        links = createLinks();
    }

    @Test
    void setAndGetSelf() {
        links.setSelf(firstSelfUrl());
        assertEquals(firstSelfUrl(), links.getSelf());
    }

    @Test
    void setAndGetCompanyProfile() {
        String companyProfileUrl = "http://example.com/company/456";
        links.setCompanyProfile(companyProfileUrl);
        assertEquals(companyProfileUrl, links.getCompanyProfile());
    }

    @Test
    void setAndGetMultipleFields() {
        String companyProfileUrl = "http://example.com/company/456";

        links.setSelf(firstSelfUrl());
        links.setCompanyProfile(companyProfileUrl);

        assertEquals(firstSelfUrl(), links.getSelf());
        assertEquals(companyProfileUrl, links.getCompanyProfile());
    }

    @Test
    void getWithoutSettingReturnsNulls() {
        assertNull(links.getSelf());
        assertNull(links.getCompanyProfile());
    }

    @Test
    void setSelfToNull() {
        links.setSelf(firstSelfUrl());
        links.setSelf(null);
        assertNull(links.getSelf());
    }

    @Test
    void setCompanyProfileToNull() {
        links.setCompanyProfile("http://example.com/company/456");
        links.setCompanyProfile(null);
        assertNull(links.getCompanyProfile());
    }

    @Test
    void setSelfMultipleTimesUsesLatestValue() {
        links.setSelf(firstSelfUrl());
        assertEquals(firstSelfUrl(), links.getSelf());

        links.setSelf(secondSelfUrl());
        assertEquals(secondSelfUrl(), links.getSelf());
    }

    @Test
    void setCompanyProfileMultipleTimesUsesLatestValue() {
        String firstCompanyProfileUrl = "http://example.com/company/123";
        String secondCompanyProfileUrl = "http://example.com/company/456";

        links.setCompanyProfile(firstCompanyProfileUrl);
        assertEquals(firstCompanyProfileUrl, links.getCompanyProfile());

        links.setCompanyProfile(secondCompanyProfileUrl);
        assertEquals(secondCompanyProfileUrl, links.getCompanyProfile());
    }

    @Test
    void emptyStringValuesAreAllowed() {
        links.setSelf("");
        links.setCompanyProfile("");

        assertEquals("", links.getSelf());
        assertEquals("", links.getCompanyProfile());
    }
}

