package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/strike-off-partner-objections-api")
@RestController
public class StrikeOffObjectionController {

    @PostMapping("/company/{company_number}/strike-off-partner-objections")
    public ResponseEntity<Void> createObjection(@PathVariable("company_number") final String companyNumber) {
        return new ResponseEntity<>(HttpStatus.CREATED);
    }
}

