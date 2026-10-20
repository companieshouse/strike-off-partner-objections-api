package uk.gov.companieshouse.strikeoffpartnerobjectionsapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import uk.gov.companieshouse.logging.util.DataMap;

import static uk.gov.companieshouse.strikeoffpartnerobjectionsapi.utils.StrikeoffPartnerObjectionsUtils.LOGGER;

@SpringBootApplication
public class StrikeOffPartnerObjectionsApiApplication {

    public static void main(String[] args) {
        var logMap = new DataMap.Builder().build().getLogMap();
        LOGGER.info("Application startup initialised", logMap);
        SpringApplication.run(StrikeOffPartnerObjectionsApiApplication.class, args);
    }

}
