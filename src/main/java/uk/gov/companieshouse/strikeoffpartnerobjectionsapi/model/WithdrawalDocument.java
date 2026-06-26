package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model;

import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

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

    private PartnerLinks links;

    private String kind;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCompanyNumber() {
        return companyNumber;
    }

    public void setCompanyNumber(String companyNumber) {
        this.companyNumber = companyNumber;
    }

    public String getSubmissionCompanyName() {
        return submissionCompanyName;
    }

    public void setSubmissionCompanyName(String submissionCompanyName) {
        this.submissionCompanyName = submissionCompanyName;
    }

    public String getWithdrawalId() {
        return withdrawalId;
    }

    public void setWithdrawalId(String withdrawalId) {
        this.withdrawalId = withdrawalId;
    }

    public String getPartnerOrganisation() {
        return partnerOrganisation;
    }

    public void setPartnerOrganisation(String partnerOrganisation) {
        this.partnerOrganisation = partnerOrganisation;
    }

    public String getPartnerContactEmail() {
        return partnerContactEmail;
    }

    public void setPartnerContactEmail(String partnerContactEmail) {
        this.partnerContactEmail = partnerContactEmail;
    }

    public String getPartnerCaseReference() {
        return partnerCaseReference;
    }

    public void setPartnerCaseReference(String partnerCaseReference) {
        this.partnerCaseReference = partnerCaseReference;
    }

    public String getPartnerObjectionWorkstream() {
        return partnerObjectionWorkstream;
    }

    public void setPartnerObjectionWorkstream(String partnerObjectionWorkstream) {
        this.partnerObjectionWorkstream = partnerObjectionWorkstream;
    }

    public String getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(String processingStatus) {
        this.processingStatus = processingStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getEtag() {
        return etag;
    }

    public void setEtag(String etag) {
        this.etag = etag;
    }

    public PartnerLinks getLinks() {
        return links;
    }

    public void setLinks(PartnerLinks links) {
        this.links = links;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

}

