package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.controller;

import org.springframework.web.server.ResponseStatusException;
import uk.gov.companieshouse.api.objections.api.StrikeOffPartnerObjectionsInterface;
import uk.gov.companieshouse.api.objections.model.BaseObjectionResponse;
import uk.gov.companieshouse.api.objections.model.CreateObjectionRequest;
import uk.gov.companieshouse.api.objections.model.UpdateObjectionStatusRequest;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.ObjectionNotFoundException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service.StrikeOffPartnerObjectionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

@RestController
public class StrikeOffObjectionPartnerController implements StrikeOffPartnerObjectionsInterface {

    private final StrikeOffPartnerObjectionService strikeOffPartnerObjectionService;

    public StrikeOffObjectionPartnerController(
            final StrikeOffPartnerObjectionService strikeOffPartnerObjectionService) {
        this.strikeOffPartnerObjectionService = strikeOffPartnerObjectionService;
    }

    @Override
    public ResponseEntity<BaseObjectionResponse> createObjection(
            @Size(min = 1) @PathVariable("company_number") final String companyNumber,
            @Valid @RequestBody final CreateObjectionRequest createObjectionRequest) {
        BaseObjectionResponse response =
                strikeOffPartnerObjectionService.createObjection(companyNumber, createObjectionRequest);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<BaseObjectionResponse> getObjection(
            @Size(min = 1) @PathVariable("company_number") final String companyNumber,
            @Size(min = 1) @PathVariable("objection_id") final String objectionId) {
        try {
            BaseObjectionResponse response =
                    strikeOffPartnerObjectionService.getObjection(companyNumber, objectionId);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (ObjectionNotFoundException ex) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    @Override
    public ResponseEntity<Void> updateObjectionStatus(
            @Size(min = 1) @PathVariable("company_number") final String companyNumber,
            @Size(min = 1) @PathVariable("objection_id") final String objectionId,
            @Valid @RequestBody final UpdateObjectionStatusRequest updateStatusRequest) {
        try {
            strikeOffPartnerObjectionService.updateObjectionProcessingStatus(
                    companyNumber,
                    objectionId,
                    updateStatusRequest);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (ObjectionNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }
}
