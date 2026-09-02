package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.companieshouse.api.objections.api.StrikeOffPartnerObjectionsInterface;
import uk.gov.companieshouse.api.objections.model.BaseObjectionResponse;
import uk.gov.companieshouse.api.objections.model.CreateObjectionRequest;
import uk.gov.companieshouse.api.objections.model.UpdateObjectionStatusRequest;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.exception.ObjectionNotFoundException;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service.StrikeOffPartnerObjectionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.ERIC_PARTNER_ORGANISATION_HEADER;
import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.validatePartnerOrganisation;

/**
 * REST controller for strike off partner objection operations.
 *
 * <p>Implements {@link StrikeOffPartnerObjectionsInterface} to expose endpoints
 * for creating, retrieving, and updating processing status of objections
 * against a company's strike-off proposal.</p>
 */
@RestController
public class StrikeOffObjectionPartnerController implements StrikeOffPartnerObjectionsInterface {

    private final StrikeOffPartnerObjectionService strikeOffPartnerObjectionService;
    private final HttpServletRequest httpServletRequest;

    /**
     * Constructs the controller with the service and request used to process objection operations.
     *
     * @param strikeOffPartnerObjectionService service handling objection creation, retrieval, and status updates
     * @param httpServletRequest               the current HTTP request used to resolve the partner organisation header
     */
    public StrikeOffObjectionPartnerController(
            StrikeOffPartnerObjectionService strikeOffPartnerObjectionService,
            HttpServletRequest httpServletRequest) {
        this.strikeOffPartnerObjectionService = strikeOffPartnerObjectionService;
        this.httpServletRequest = httpServletRequest;
    }

    /**
     * Creates a new strike-off objection for the specified company.
     *
     * @param companyNumber          the company number to raise an objection against
     * @param createObjectionRequest request payload containing the objection details
     * @return a response entity containing the created objection record and HTTP 201 status
     */
    @Override
    public ResponseEntity<BaseObjectionResponse> createObjection(
            @Size(min = 1) @PathVariable("company_number") String companyNumber,
            @Valid @RequestBody CreateObjectionRequest createObjectionRequest) {
        String partnerOrganisation = resolvePartnerOrganisation();
        BaseObjectionResponse response =
                strikeOffPartnerObjectionService.createObjection(companyNumber, createObjectionRequest, partnerOrganisation);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Retrieves a single objection for the provided company and objection identifiers.
     *
     * @param companyNumber the company number the objection belongs to
     * @param objectionId   the unique objection identifier
     * @return a response entity containing the matched objection and HTTP 200 status
     * @throws ResponseStatusException with HTTP 404 if the objection is not found
     */
    @Override
    public ResponseEntity<BaseObjectionResponse> getObjection(
            @Size(min = 1) @PathVariable("company_number") String companyNumber,
            @Size(min = 1) @PathVariable("objection_id") String objectionId) {
        String partnerOrganisation = resolvePartnerOrganisation();
        try {
            BaseObjectionResponse response =
                    strikeOffPartnerObjectionService.getObjection(companyNumber, objectionId, partnerOrganisation);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (ObjectionNotFoundException ex) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    /**
     * Updates the processing status of an existing objection.
     *
     * @param companyNumber       the company number the objection belongs to
     * @param objectionId         the unique objection identifier
     * @param updateStatusRequest request payload containing the new processing status
     * @return a response entity with HTTP 204 on success
     * @throws ResponseStatusException with HTTP 404 if the objection is not found
     */
    @Override
    public ResponseEntity<Void> updateObjectionStatus(
            @Size(min = 1) @PathVariable("company_number") String companyNumber,
            @Size(min = 1) @PathVariable("objection_id") String objectionId,
            @Valid @RequestBody UpdateObjectionStatusRequest updateStatusRequest) {
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

    private String resolvePartnerOrganisation() {
        String partnerOrganisation = httpServletRequest.getHeader(ERIC_PARTNER_ORGANISATION_HEADER);
        return validatePartnerOrganisation(partnerOrganisation);
    }
}
