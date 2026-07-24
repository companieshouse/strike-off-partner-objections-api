package uk.gov.companieshouse.strikeoffpartnerobjectionsapi;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.companieshouse.api.InternalApiClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Tag("integration-test")
class StrikeOffPartnerObjectionsApiApplicationTest {

    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    private InternalApiClient internalApiClient;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void applicationContextLoads() {
        assertThat(applicationContext).isNotNull();
    }
}
