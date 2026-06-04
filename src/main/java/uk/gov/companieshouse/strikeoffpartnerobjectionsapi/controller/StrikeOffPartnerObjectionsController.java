package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.companieshouse.api.objections.api.StrikeOffPartnerObjectionsInterface;
import uk.gov.companieshouse.api.objections.model.BaseObjectionResponse;
import uk.gov.companieshouse.api.objections.model.CreateObjectionRequest;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.ObjectionPersistenceException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service.ObjectionService;

@RestController
public class StrikeOffPartnerObjectionsController implements StrikeOffPartnerObjectionsInterface {

    private final ObjectionService objectionService;

    public StrikeOffPartnerObjectionsController(ObjectionService objectionService) {
        this.objectionService = objectionService;
    }

    @Override
    public ResponseEntity<BaseObjectionResponse> createObjection(String companyNumber,
                                                                 CreateObjectionRequest createObjectionRequest) {
        try {
            BaseObjectionResponse response =
                    objectionService.createObjection(companyNumber, createObjectionRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (ObjectionPersistenceException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public ResponseEntity<BaseObjectionResponse> getObjection(String companyNumber, String objectionId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
