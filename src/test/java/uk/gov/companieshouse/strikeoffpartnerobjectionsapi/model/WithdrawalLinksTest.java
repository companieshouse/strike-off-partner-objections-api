package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit-test")
class WithdrawalLinksTest {

    private WithdrawalLinks withdrawalLinks;

    @BeforeEach
    void setUp() {
        withdrawalLinks = new WithdrawalLinks();
    }

    @Test
    void testSetAndGetSelf() {
        String selfUrl = "http://example.com/withdrawals/123";
        withdrawalLinks.setSelf(selfUrl);
        assertEquals(selfUrl, withdrawalLinks.getSelf());
    }

    @Test
    void testSetAndGetCompanyProfile() {
        String companyProfileUrl = "http://example.com/company/456";
        withdrawalLinks.setCompanyProfile(companyProfileUrl);
        assertEquals(companyProfileUrl, withdrawalLinks.getCompanyProfile());
    }

    @Test
    void testSetAndGetMultipleFields() {
        String selfUrl = "http://example.com/withdrawals/123";
        String companyProfileUrl = "http://example.com/company/456";

        withdrawalLinks.setSelf(selfUrl);
        withdrawalLinks.setCompanyProfile(companyProfileUrl);

        assertEquals(selfUrl, withdrawalLinks.getSelf());
        assertEquals(companyProfileUrl, withdrawalLinks.getCompanyProfile());
    }

    @Test
    void testGetWithoutSetting() {
        assertNull(withdrawalLinks.getSelf());
        assertNull(withdrawalLinks.getCompanyProfile());
    }

    @Test
    void testSetSelfToNull() {
        withdrawalLinks.setSelf("http://example.com/withdrawals/123");
        withdrawalLinks.setSelf(null);
        assertNull(withdrawalLinks.getSelf());
    }

    @Test
    void testSetCompanyProfileToNull() {
        withdrawalLinks.setCompanyProfile("http://example.com/company/456");
        withdrawalLinks.setCompanyProfile(null);
        assertNull(withdrawalLinks.getCompanyProfile());
    }

    @Test
    void testSetSelfMultipleTimes() {
        String firstUrl = "http://example.com/withdrawals/123";
        String secondUrl = "http://example.com/withdrawals/456";

        withdrawalLinks.setSelf(firstUrl);
        assertEquals(firstUrl, withdrawalLinks.getSelf());

        withdrawalLinks.setSelf(secondUrl);
        assertEquals(secondUrl, withdrawalLinks.getSelf());
    }

    @Test
    void testSetCompanyProfileMultipleTimes() {
        String firstUrl = "http://example.com/company/123";
        String secondUrl = "http://example.com/company/456";

        withdrawalLinks.setCompanyProfile(firstUrl);
        assertEquals(firstUrl, withdrawalLinks.getCompanyProfile());

        withdrawalLinks.setCompanyProfile(secondUrl);
        assertEquals(secondUrl, withdrawalLinks.getCompanyProfile());
    }

    @Test
    void testEmptyStringValues() {
        withdrawalLinks.setSelf("");
        withdrawalLinks.setCompanyProfile("");

        assertEquals("", withdrawalLinks.getSelf());
        assertEquals("", withdrawalLinks.getCompanyProfile());
    }
}

