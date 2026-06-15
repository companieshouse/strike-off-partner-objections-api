package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.controller;

import uk.gov.companieshouse.api.objections.api.StrikeOffPartnerObjectionsInterface;
import uk.gov.companieshouse.api.objections.model.BaseObjectionResponse;
import uk.gov.companieshouse.api.objections.model.CreateObjectionRequest;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service.StrikeOffObjectionPartnerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StrikeOffObjectionPartnerController implements StrikeOffPartnerObjectionsInterface {

    private final StrikeOffObjectionPartnerService strikeOffObjectionPartnerService;

    public StrikeOffObjectionPartnerController(
            final StrikeOffObjectionPartnerService strikeOffObjectionPartnerService) {
        this.strikeOffObjectionPartnerService = strikeOffObjectionPartnerService;
    }

    @Override
    public ResponseEntity<BaseObjectionResponse> createObjection(
            final String companyNumber,
            final CreateObjectionRequest createObjectionRequest) {
        BaseObjectionResponse response =
                strikeOffObjectionPartnerService.createObjection(companyNumber, createObjectionRequest);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<BaseObjectionResponse> getObjection(
            final String companyNumber,
            final String objectionId) {
        BaseObjectionResponse response =
                strikeOffObjectionPartnerService.getObjection(companyNumber, objectionId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
