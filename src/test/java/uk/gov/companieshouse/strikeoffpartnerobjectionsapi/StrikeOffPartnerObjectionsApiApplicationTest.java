package uk.gov.companieshouse.strikeoffpartnerobjectionsapi;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

@Tag("unit-test")
class StrikeOffPartnerObjectionsApiApplicationTest {

    @Test
    void main_whenInvoked_startsSpringApplication() {
        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            springApplication.when(() -> SpringApplication.run(
                    eq(StrikeOffPartnerObjectionsApiApplication.class), any(String[].class)))
                    .thenReturn(null);

            StrikeOffPartnerObjectionsApiApplication.main(new String[]{});

            springApplication.verify(() -> SpringApplication.run(
                    eq(StrikeOffPartnerObjectionsApiApplication.class), any(String[].class)));
        }
    }
}

