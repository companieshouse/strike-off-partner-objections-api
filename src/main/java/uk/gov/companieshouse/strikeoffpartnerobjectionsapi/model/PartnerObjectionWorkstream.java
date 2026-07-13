package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum PartnerObjectionWorkstream {
    INDIVIDUALS_AND_SMALL_BUSINESS_COMPLIANCE("individuals-and-small-business-compliance"),
    WEALTHY_AND_MID_SIZED_BUSINESS_COMPLIANCE("wealthy-and-mid-sized-business-compliance"),
    DEBT_MANAGEMENT("debt-management");

    private static final Set<String> VALID_VALUES = Arrays.stream(values())
            .map(PartnerObjectionWorkstream::value)
            .collect(Collectors.toUnmodifiableSet());

    private final String value;

    PartnerObjectionWorkstream(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static boolean isValid(String workstream) {
        return VALID_VALUES.contains(workstream);
    }
}

