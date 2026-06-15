package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
public class WithdrawalLinks {

    private String self;

    @Field("company_profile")
    private String companyProfile;
}