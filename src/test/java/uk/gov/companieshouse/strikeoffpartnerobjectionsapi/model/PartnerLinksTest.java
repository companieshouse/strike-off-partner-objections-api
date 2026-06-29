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
    void setAndGetSelf() {
        links.setSelf("http://example.com/resource/123");
        assertEquals("http://example.com/resource/123", links.getSelf());
    }

    @Test
    void setAndGetCompanyProfile() {
        links.setCompanyProfile("http://example.com/company/456");
        assertEquals("http://example.com/company/456", links.getCompanyProfile());
    }

    @Test
    void setAndGetMultipleFields() {
        links.setSelf("http://example.com/resource/123");
        links.setCompanyProfile("http://example.com/company/456");

        assertEquals("http://example.com/resource/123", links.getSelf());
        assertEquals("http://example.com/company/456", links.getCompanyProfile());
    }

    @Test
    void getWithoutSettingReturnsNulls() {
        assertNull(links.getSelf());
        assertNull(links.getCompanyProfile());
    }

    @Test
    void setSelfToNull() {
        links.setSelf("http://example.com/resource/123");
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
        links.setSelf("http://example.com/resource/123");
        assertEquals("http://example.com/resource/123", links.getSelf());

        links.setSelf("http://example.com/resource/456");
        assertEquals("http://example.com/resource/456", links.getSelf());
    }

    @Test
    void setCompanyProfileMultipleTimesUsesLatestValue() {
        links.setCompanyProfile("http://example.com/company/123");
        assertEquals("http://example.com/company/123", links.getCompanyProfile());

        links.setCompanyProfile("http://example.com/company/456");
        assertEquals("http://example.com/company/456", links.getCompanyProfile());
    }

    @Test
    void emptyStringValuesAreAllowed() {
        links.setSelf("");
        links.setCompanyProfile("");

        assertEquals("", links.getSelf());
        assertEquals("", links.getCompanyProfile());
    }
}

