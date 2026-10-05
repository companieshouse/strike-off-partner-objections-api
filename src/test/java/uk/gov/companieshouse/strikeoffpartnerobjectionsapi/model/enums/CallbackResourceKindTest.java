package uk.gov.companieshouse.strikeoffpartnerobjectionsapi.model.enums;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit-test")
class CallbackResourceKindTest {

    @ParameterizedTest
    @EnumSource(CallbackResourceKind.class)
    void enumValues_areNotNull(CallbackResourceKind kind) {
        assertThat(kind).isNotNull();
    }

    @Test
    void objection_enumConstant_exists() {
        assertThat(CallbackResourceKind.OBJECTION).isNotNull();
    }

    @Test
    void withdrawal_enumConstant_exists() {
        assertThat(CallbackResourceKind.WITHDRAWAL).isNotNull();
    }

    @Test
    void enumValues_containsExpectedConstants() {
        CallbackResourceKind[] values = CallbackResourceKind.values();
        assertThat(values).containsExactlyInAnyOrder(
                CallbackResourceKind.OBJECTION,
                CallbackResourceKind.WITHDRAWAL
        );
    }

    @ParameterizedTest
    @EnumSource(CallbackResourceKind.class)
    void enumName_isNotEmpty(CallbackResourceKind kind) {
        assertThat(kind.name()).isNotEmpty();
    }

    @Test
    void objection_valueOf_returnsCorrectConstant() {
        assertThat(CallbackResourceKind.valueOf("OBJECTION"))
                .isEqualTo(CallbackResourceKind.OBJECTION);
    }

    @Test
    void withdrawal_valueOf_returnsCorrectConstant() {
        assertThat(CallbackResourceKind.valueOf("WITHDRAWAL"))
                .isEqualTo(CallbackResourceKind.WITHDRAWAL);
    }
}

