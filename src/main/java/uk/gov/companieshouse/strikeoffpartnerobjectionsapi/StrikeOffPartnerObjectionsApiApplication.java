package uk.gov.companieshouse.strikeoffpartnerobjectionsapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class StrikeOffPartnerObjectionsApiApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            StrikeOffPartnerObjectionsApiApplication.class);

    public static void main(String[] args) {
        LOGGER.info("Starting strike-off-partner-objections-api application!!!!!");
        SpringApplication.run(StrikeOffPartnerObjectionsApiApplication.class, args);
    }

}
