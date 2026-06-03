package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public class CreateObjectionRequest {

    @NotBlank
    @JsonProperty("submission_company_name")
    private String submissionCompanyName;

    @NotBlank
    @JsonProperty("partner_case_reference")
    private String partnerCaseReference;

    @NotBlank
    @JsonProperty("partner_objection_workstream")
    private String partnerObjectionWorkstream;

    @NotBlank
    @JsonProperty("partner_contact_email")
    private String partnerContactEmail;

    @NotBlank
    @JsonProperty("partner_objection_reason")
    private String partnerObjectionReason;

    public void setSubmissionCompanyName(final String submissionCompanyName) {
        this.submissionCompanyName = submissionCompanyName;
    }

    public String getPartnerCaseReference() {
        return partnerCaseReference;
    }

    public void setPartnerCaseReference(final String partnerCaseReference) {
        this.partnerCaseReference = partnerCaseReference;
    }

    public String getPartnerObjectionWorkstream() {
        return partnerObjectionWorkstream;
    }

    public void setPartnerObjectionWorkstream(final String partnerObjectionWorkstream) {
        this.partnerObjectionWorkstream = partnerObjectionWorkstream;
    }

    public String getPartnerContactEmail() {
        return partnerContactEmail;
    }

    public void setPartnerContactEmail(final String partnerContactEmail) {
        this.partnerContactEmail = partnerContactEmail;
    }

    public String getPartnerObjectionReason() {
        return partnerObjectionReason;
    }

    public void setPartnerObjectionReason(final String partnerObjectionReason) {
        this.partnerObjectionReason = partnerObjectionReason;
    }
}

