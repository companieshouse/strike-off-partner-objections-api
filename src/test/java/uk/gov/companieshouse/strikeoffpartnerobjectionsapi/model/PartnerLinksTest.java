package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit-test")
class PartnerLinksTest {

    private PartnerLinks links;

    @BeforeEach
    void setUp() {
        links = new PartnerLinks();
    }

    @Test
    void setSelf_whenValueIsProvided_returnsSameValue() {
        links.setSelf("http://example.com/resource/123");
        assertEquals("http://example.com/resource/123", links.getSelf());
    }

    @Test
    void setCompanyProfile_whenValueIsProvided_returnsSameValue() {
        links.setCompanyProfile("http://example.com/company/456");
        assertEquals("http://example.com/company/456", links.getCompanyProfile());
    }

    @Test
    void setSelfAndCompanyProfile_whenValuesAreProvided_returnsBothValues() {
        links.setSelf("http://example.com/resource/123");
        links.setCompanyProfile("http://example.com/company/456");

        assertEquals("http://example.com/resource/123", links.getSelf());
        assertEquals("http://example.com/company/456", links.getCompanyProfile());
    }

    @Test
    void getSelfAndCompanyProfile_whenValuesAreNotSet_returnsNulls() {
        assertNull(links.getSelf());
        assertNull(links.getCompanyProfile());
    }

    @Test
    void setSelf_whenSetToNull_returnsNull() {
        links.setSelf("http://example.com/resource/123");
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
        links.setSelf("http://example.com/resource/123");
        assertEquals("http://example.com/resource/123", links.getSelf());

        links.setSelf("http://example.com/resource/456");
        assertEquals("http://example.com/resource/456", links.getSelf());
    }

    @Test
    void setCompanyProfile_whenSetMultipleTimes_returnsLatestValue() {
        links.setCompanyProfile("http://example.com/company/123");
        assertEquals("http://example.com/company/123", links.getCompanyProfile());

        links.setCompanyProfile("http://example.com/company/456");
        assertEquals("http://example.com/company/456", links.getCompanyProfile());
    }

    @Test
    void setSelfAndCompanyProfile_whenValuesAreEmptyStrings_returnsEmptyStrings() {
        links.setSelf("");
        links.setCompanyProfile("");

        assertEquals("", links.getSelf());
        assertEquals("", links.getCompanyProfile());
    }
}

