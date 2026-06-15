package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@Document(collection = "withdrawals")
public class WithdrawalDocument {

    @Id
    private String id;

    @Field("company_number")
    @Indexed
    private String companyNumber;

    @Field("submission_company_name")
    private String submissionCompanyName;

    @Field("withdrawal_id")
    @Indexed(unique = true)
    private String withdrawalId;

    @Field("partner_organisation")
    private String partnerOrganisation;

    @Field("partner_contact_email")
    private String partnerContactEmail;

    @Field("partner_case_reference")
    private String partnerCaseReference;

    @Field("partner_objection_workstream")
    private String partnerObjectionWorkstream;

    @Field("processing_status")
    @Indexed
    private String processingStatus;

    @CreatedDate
    @Field("created_at")
    private Instant createdAt;

    private String etag;

    private WithdrawalLinks links;

    private String kind;
}