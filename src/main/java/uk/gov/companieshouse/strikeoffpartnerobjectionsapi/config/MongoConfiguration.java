package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

@Configuration
public class MongoConfiguration {
    @Bean
    MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(
                new OffsetDateTimeToStringConverter(),
                new StringToOffsetDateTimeConverter()
        ));
    }

    @WritingConverter
    static class OffsetDateTimeToStringConverter implements Converter<OffsetDateTime, String> {
        @Override
        public String convert(OffsetDateTime source) {
            return source == null ? null
                    : source.withOffsetSameInstant(ZoneOffset.UTC)
                      .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
    }

    @ReadingConverter
    static class StringToOffsetDateTimeConverter implements Converter<String, OffsetDateTime> {
        @Override
        public OffsetDateTime convert(String source) {
            return source == null ? null
                    : OffsetDateTime.parse(source, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
    }
}

