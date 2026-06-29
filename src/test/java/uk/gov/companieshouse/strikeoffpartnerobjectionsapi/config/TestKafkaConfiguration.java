package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config;

import consumer.deserialization.AvroDeserializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import uk.gov.companieshouse.api.InternalApiClient;
import uk.gov.companieshouse.api.handler.company.CompanyResourceHandler;
import uk.gov.companieshouse.api.handler.company.request.CompanyGet;
import uk.gov.companieshouse.api.model.ApiResponse;
import uk.gov.companieshouse.api.model.company.CompanyProfileApi;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@TestConfiguration
public class TestKafkaConfiguration {
    @Bean
    KafkaConsumer<String, byte[]> testConsumer(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${kafka.topic.strikeoff.partner.objections:strikeoff-partner-objections-incoming}") String topic) {

        KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(
                Map.of(
                        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, AvroDeserializer.class,
                        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, AvroDeserializer.class,
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false",
                        ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString()),
                new StringDeserializer(), new ByteArrayDeserializer());

        consumer.subscribe(List.of(topic));
        return consumer;
    }

    @Bean
    @Primary
    InternalApiClient internalApiClient() throws Exception {
        InternalApiClient mockClient = Mockito.mock(InternalApiClient.class);
        CompanyResourceHandler companyHandler = Mockito.mock(CompanyResourceHandler.class);
        CompanyGet companyGet = Mockito.mock(CompanyGet.class);
        ApiResponse<CompanyProfileApi> apiResponse = Mockito.mock(ApiResponse.class);
        CompanyProfileApi companyProfile = new CompanyProfileApi();
        companyProfile.setCompanyName("Test Company");

        when(mockClient.company()).thenReturn(companyHandler);
        when(companyHandler.get(anyString())).thenReturn(companyGet);
        when(companyGet.execute()).thenReturn(apiResponse);
        when(apiResponse.getData()).thenReturn(companyProfile);

        return mockClient;
    }
}
