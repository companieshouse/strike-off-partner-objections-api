package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload for HMRC outcome callback notifications.
 *
 * <p>Contains the minimal set of fields required to notify HMRC of processing outcomes
 * for objection and withdrawal resources. All fields are required.</p>
 */
public class HmrcCallbackPayload {

    @JsonProperty("resource_kind")
    private CallbackResourceKind resourceKind;

    @JsonProperty("resource_id")
    private String resourceId;

    @JsonProperty("company_number")
    private String companyNumber;

    @JsonProperty("resource_uri")
    private String resourceUri;

    /**
     * Constructs a callback payload with the specified parameters.
     *
     * @param resourceKind the type of resource (OBJECTION or WITHDRAWAL)
     * @param resourceId the unique identifier of the resource
     * @param companyNumber the company number associated with the resource
     * @param resourceUri the GET endpoint URI for retrieving the resource
     */
    public HmrcCallbackPayload(CallbackResourceKind resourceKind, String resourceId, String companyNumber,
                               String resourceUri) {
        this.resourceKind = resourceKind;
        this.resourceId = resourceId;
        this.companyNumber = companyNumber;
        this.resourceUri = resourceUri;
    }

    /**
     * Default constructor for deserialization.
     */
    public HmrcCallbackPayload() {
    }

    public CallbackResourceKind getResourceKind() {
        return resourceKind;
    }

    public void setResourceKind(CallbackResourceKind resourceKind) {
        this.resourceKind = resourceKind;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getCompanyNumber() {
        return companyNumber;
    }

    public void setCompanyNumber(String companyNumber) {
        this.companyNumber = companyNumber;
    }

    public String getResourceUri() {
        return resourceUri;
    }

    public void setResourceUri(String resourceUri) {
        this.resourceUri = resourceUri;
    }
}
