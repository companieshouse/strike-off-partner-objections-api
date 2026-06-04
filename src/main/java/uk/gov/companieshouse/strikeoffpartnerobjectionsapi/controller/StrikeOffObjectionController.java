package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.controller;

import uk.gov.companieshouse.api.objections.api.StrikeOffPartnerObjectionsInterface;
import uk.gov.companieshouse.api.objections.model.BaseObjectionResponse;
import uk.gov.companieshouse.api.objections.model.CreateObjectionRequest;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service.StrikeOffObjectionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StrikeOffObjectionController implements StrikeOffPartnerObjectionsInterface {

    private final StrikeOffObjectionService strikeOffObjectionService;

    public StrikeOffObjectionController(final StrikeOffObjectionService strikeOffObjectionService) {
        this.strikeOffObjectionService = strikeOffObjectionService;
    }

    @Override
    public ResponseEntity<BaseObjectionResponse> createObjection(
            final String companyNumber,
            final CreateObjectionRequest createObjectionRequest) {
        BaseObjectionResponse response = strikeOffObjectionService.createObjection(companyNumber, createObjectionRequest);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<BaseObjectionResponse> getObjection(final String companyNumber, final String objectionId) {
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
    }
}

