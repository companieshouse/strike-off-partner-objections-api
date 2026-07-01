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
    void setSelf_whenValueIsProvided_returnsSameValue() {
        links.setSelf(firstSelfUrl());
        assertEquals(firstSelfUrl(), links.getSelf());
    }

    @Test
    void setCompanyProfile_whenValueIsProvided_returnsSameValue() {
        String companyProfileUrl = "http://example.com/company/456";
        links.setCompanyProfile(companyProfileUrl);
        assertEquals(companyProfileUrl, links.getCompanyProfile());
    }

    @Test
    void setSelfAndCompanyProfile_whenValuesAreProvided_returnsBothValues() {
        String companyProfileUrl = "http://example.com/company/456";

        links.setSelf(firstSelfUrl());
        links.setCompanyProfile(companyProfileUrl);

        assertEquals(firstSelfUrl(), links.getSelf());
        assertEquals(companyProfileUrl, links.getCompanyProfile());
    }

    @Test
    void getSelfAndCompanyProfile_whenValuesAreNotSet_returnsNulls() {
        assertNull(links.getSelf());
        assertNull(links.getCompanyProfile());
    }

    @Test
    void setSelf_whenSetToNull_returnsNull() {
        links.setSelf(firstSelfUrl());
        links.setSelf(null);
        assertNull(links.getSelf());
    }

    @Test
    void setCompanyProfile_whenSetToNull_returnsNull() {
        links.setCompanyProfile("http://example.com/company/456");
        links.setCompanyProfile(null);
        assertNull(links.getCompanyProfile());
    }

    @Test
    void setSelf_whenSetMultipleTimes_returnsLatestValue() {
        links.setSelf(firstSelfUrl());
        assertEquals(firstSelfUrl(), links.getSelf());

        links.setSelf(secondSelfUrl());
        assertEquals(secondSelfUrl(), links.getSelf());
    }

    @Test
    void setCompanyProfile_whenSetMultipleTimes_returnsLatestValue() {
        String firstCompanyProfileUrl = "http://example.com/company/123";
        String secondCompanyProfileUrl = "http://example.com/company/456";

        links.setCompanyProfile(firstCompanyProfileUrl);
        assertEquals(firstCompanyProfileUrl, links.getCompanyProfile());

        links.setCompanyProfile(secondCompanyProfileUrl);
        assertEquals(secondCompanyProfileUrl, links.getCompanyProfile());
    }

    @Test
    void setSelfAndCompanyProfile_whenValuesAreEmptyStrings_returnsEmptyStrings() {
        links.setSelf("");
        links.setCompanyProfile("");

        assertEquals("", links.getSelf());
        assertEquals("", links.getCompanyProfile());
    }
}

