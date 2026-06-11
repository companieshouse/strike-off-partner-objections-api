package uk.gov.companieshouse.strikeoffpartnerobjectionsapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;

@SpringBootApplication
public class StrikeOffPartnerObjectionsApiApplication {

    public static void main(String[] args) {
        LOGGER.info("Starting strike-off-partner-objections-api application!");
        SpringApplication.run(StrikeOffPartnerObjectionsApiApplication.class, args);
    }

}
