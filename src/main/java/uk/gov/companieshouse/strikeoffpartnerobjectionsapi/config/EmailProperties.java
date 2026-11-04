package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.config;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "email")
public record EmailProperties(List<String> allowedDomains) {

    public EmailProperties(@Nullable List<String> allowedDomains) {
        this.allowedDomains = allowedDomains == null
                ? List.of()
                : List.copyOf(allowedDomains);
    }
}