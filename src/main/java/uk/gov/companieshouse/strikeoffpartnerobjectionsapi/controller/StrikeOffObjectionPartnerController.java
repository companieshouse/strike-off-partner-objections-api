package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.controller;

import org.springframework.web.server.ResponseStatusException;
import uk.gov.companieshouse.api.objections.api.StrikeOffPartnerObjectionsInterface;
import uk.gov.companieshouse.api.objections.model.BaseObjectionResponse;
import uk.gov.companieshouse.api.objections.model.CreateObjectionRequest;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.ObjectionNotFoundException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service.StrikeOffPartnerObjectionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StrikeOffObjectionPartnerController implements StrikeOffPartnerObjectionsInterface {

    private final StrikeOffPartnerObjectionService strikeOffPartnerObjectionService;

    public StrikeOffObjectionPartnerController(
            final StrikeOffPartnerObjectionService strikeOffPartnerObjectionService) {
        this.strikeOffPartnerObjectionService = strikeOffPartnerObjectionService;
    }

    @Override
    public ResponseEntity<BaseObjectionResponse> createObjection(
            final String companyNumber,
            final CreateObjectionRequest createObjectionRequest) {
        BaseObjectionResponse response =
                strikeOffPartnerObjectionService.createObjection(companyNumber, createObjectionRequest);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<BaseObjectionResponse> getObjection(
            final String companyNumber,
            final String objectionId) {
        try {
            BaseObjectionResponse response =
                    strikeOffPartnerObjectionService.getObjection(companyNumber, objectionId);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (ObjectionNotFoundException ex) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }
}
