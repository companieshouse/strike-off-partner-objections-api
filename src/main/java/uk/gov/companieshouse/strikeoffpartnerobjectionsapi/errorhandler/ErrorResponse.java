package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.errorhandler;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ErrorResponse {

    @JsonProperty("error")
    private String error;

    public ErrorResponse() {
    }

    public ErrorResponse(final String error) {
        this.error = error;
    }

}

