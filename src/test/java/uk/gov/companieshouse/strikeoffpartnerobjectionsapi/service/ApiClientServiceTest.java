package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uk.gov.companieshouse.api.InternalApiClient;

@Tag("unit-test")
class ApiClientServiceTest {

    @Test
    void getInternalApiClient_whenCalled_returnsInjectedClient() {
        InternalApiClient internalApiClient = mock(InternalApiClient.class);
        ApiClientService service = new ApiClientService(internalApiClient);

        InternalApiClient result = service.getInternalApiClient();

        assertThat(result).isSameAs(internalApiClient);
    }
}

