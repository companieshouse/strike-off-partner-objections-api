package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public class UpdateObjectionProcessingStatusRequest {

    @JsonProperty("processing_status")
    @NotBlank
    private String processingStatus;

    public String getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(String processingStatus) {
        this.processingStatus = processingStatus;
    }
}

