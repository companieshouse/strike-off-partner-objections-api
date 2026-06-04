package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.controller;

import jakarta.validation.Valid;
import uk.gov.companieshouse.api.objections.model.CreateObjectionRequest;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.dto.BaseObjectionResponse;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service.StrikeOffObjectionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/strike-off-partner-objections-api")
@RestController
public class StrikeOffObjectionController {

    private final StrikeOffObjectionService strikeOffObjectionService;

    public StrikeOffObjectionController(final StrikeOffObjectionService strikeOffObjectionService) {
        this.strikeOffObjectionService = strikeOffObjectionService;
    }

    @PostMapping("/company/{company_number}/strike-off-partner-objections")
    public ResponseEntity<BaseObjectionResponse> createObjection(
            @PathVariable("company_number") final String companyNumber,
            @Valid @RequestBody final CreateObjectionRequest createObjectionRequest) {
        BaseObjectionResponse response = strikeOffObjectionService.createObjection(companyNumber, createObjectionRequest);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}

