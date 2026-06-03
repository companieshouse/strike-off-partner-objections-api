package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;

public class BaseObjectionResponse {

    @JsonProperty("objection_id")
    private String objectionId;

    @JsonProperty("processing_status")
    private String processingStatus;

    @JsonProperty("links")
    private Map<String, String> links;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("etag")
    private String etag;

    public BaseObjectionResponse() {
    }

    public BaseObjectionResponse(final String objectionId,
                                 final String processingStatus,
                                 final Map<String, String> links,
                                 final Instant createdAt,
                                 final String etag) {
        this.objectionId = objectionId;
        this.processingStatus = processingStatus;
        this.links = links;
        this.createdAt = createdAt;
        this.etag = etag;
    }

    public String getObjectionId() {
        return objectionId;
    }

    public void setObjectionId(final String objectionId) {
        this.objectionId = objectionId;
    }

    public String getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(final String processingStatus) {
        this.processingStatus = processingStatus;
    }

    public Map<String, String> getLinks() {
        return links;
    }

    public void setLinks(final Map<String, String> links) {
        this.links = links;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getEtag() {
        return etag;
    }

    public void setEtag(final String etag) {
        this.etag = etag;
    }
}

