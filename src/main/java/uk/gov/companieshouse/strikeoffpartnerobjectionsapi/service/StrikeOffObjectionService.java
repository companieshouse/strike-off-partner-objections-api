package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import java.time.Instant;
import java.util.UUID;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.dto.BaseObjectionResponse;
import uk.gov.companieshouse.strikeoffpartnerobjectionsapi.dto.CreateObjectionRequest;

@Service
public class StrikeOffObjectionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StrikeOffObjectionService.class);
    private static final String INITIAL_PROCESSING_STATUS = "PENDING";

    public BaseObjectionResponse createObjection(final String companyNumber,
                                                 final CreateObjectionRequest createObjectionRequest) {
        LOGGER.info("Creating objection for company number {}",
                companyNumber);

        final String objectionId = UUID.randomUUID().toString();
        final String selfLink = String.format(
                "/company/%s/strike-off-partner-objections/%s",
                companyNumber,
                objectionId);

        return new BaseObjectionResponse(
                objectionId,
                INITIAL_PROCESSING_STATUS,
                Map.of("self", selfLink),
                Instant.now(),
                UUID.randomUUID().toString());
    }
}

