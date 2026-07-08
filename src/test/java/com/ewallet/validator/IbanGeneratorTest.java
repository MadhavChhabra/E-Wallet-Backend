package com.ewallet.validator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IbanGeneratorTest {

    private final IbanValidator validator = new IbanValidator();

    @Test
    void generatedIbansAreValidAndUnique() {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 500; i++) {
            String iban = IbanGenerator.generate();
            assertThat(validator.isValid(iban, null))
                    .as("generated IBAN %s should pass validation", iban)
                    .isTrue();
            assertThat(iban.length()).isBetween(15, 34);
            seen.add(iban);
        }
        // Random 16-digit BBANs — collisions across 500 should be vanishingly rare.
        assertThat(seen).hasSizeGreaterThan(495);
    }
}
