package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit-test")
class ObjectionLinksTest {

    private ObjectionLinks objectionLinks;

    @BeforeEach
    void setUp() {
        objectionLinks = new ObjectionLinks();
    }

    @Test
    void testSetAndGetSelf() {
        String selfUrl = "http://example.com/objections/123";
        objectionLinks.setSelf(selfUrl);
        assertEquals(selfUrl, objectionLinks.getSelf());
    }

    @Test
    void testSetAndGetCompanyProfile() {
        String companyProfileUrl = "http://example.com/company/456";
        objectionLinks.setCompanyProfile(companyProfileUrl);
        assertEquals(companyProfileUrl, objectionLinks.getCompanyProfile());
    }

    @Test
    void testSetAndGetMultipleFields() {
        String selfUrl = "http://example.com/objections/123";
        String companyProfileUrl = "http://example.com/company/456";

        objectionLinks.setSelf(selfUrl);
        objectionLinks.setCompanyProfile(companyProfileUrl);

        assertEquals(selfUrl, objectionLinks.getSelf());
        assertEquals(companyProfileUrl, objectionLinks.getCompanyProfile());
    }

    @Test
    void testGetWithoutSetting() {
        assertNull(objectionLinks.getSelf());
        assertNull(objectionLinks.getCompanyProfile());
    }

    @Test
    void testSetSelfToNull() {
        objectionLinks.setSelf("http://example.com/objections/123");
        objectionLinks.setSelf(null);
        assertNull(objectionLinks.getSelf());
    }

    @Test
    void testSetCompanyProfileToNull() {
        objectionLinks.setCompanyProfile("http://example.com/company/456");
        objectionLinks.setCompanyProfile(null);
        assertNull(objectionLinks.getCompanyProfile());
    }

    @Test
    void testSetSelfMultipleTimes() {
        String firstUrl = "http://example.com/objections/123";
        String secondUrl = "http://example.com/objections/456";

        objectionLinks.setSelf(firstUrl);
        assertEquals(firstUrl, objectionLinks.getSelf());

        objectionLinks.setSelf(secondUrl);
        assertEquals(secondUrl, objectionLinks.getSelf());
    }

    @Test
    void testSetCompanyProfileMultipleTimes() {
        String firstUrl = "http://example.com/company/123";
        String secondUrl = "http://example.com/company/456";

        objectionLinks.setCompanyProfile(firstUrl);
        assertEquals(firstUrl, objectionLinks.getCompanyProfile());

        objectionLinks.setCompanyProfile(secondUrl);
        assertEquals(secondUrl, objectionLinks.getCompanyProfile());
    }

    @Test
    void testEmptyStringValues() {
        objectionLinks.setSelf("");
        objectionLinks.setCompanyProfile("");

        assertEquals("", objectionLinks.getSelf());
        assertEquals("", objectionLinks.getCompanyProfile());
    }
}

