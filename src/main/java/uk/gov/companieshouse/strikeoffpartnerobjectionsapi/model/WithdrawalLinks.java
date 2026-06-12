package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model;

import org.springframework.data.mongodb.core.mapping.Field;

public class WithdrawalLinks {

    private String self;

    @Field("company_profile")
    private String companyProfile;

    public String getSelf() {
        return self;
    }

    public void setSelf(String self) {
        this.self = self;
    }

    public String getCompanyProfile() {
        return companyProfile;
    }

    public void setCompanyProfile(String companyProfile) {
        this.companyProfile = companyProfile;
    }
}

