package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class UpdateWithdrawalStatusRequest {

    private static final String VALID_STATUS_PATTERN = "withdrawal-requested|withdrawal-processing|withdrawal-accepted|withdrawal-rejected";

    @JsonProperty("processing_status")
    @NotBlank
    @Pattern(regexp = VALID_STATUS_PATTERN)
    private String processingStatus;

    public String getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(String processingStatus) {
        this.processingStatus = processingStatus;
    }
}
