package com.ewallet.validator;

import java.security.SecureRandom;

/**
 * Generates IBANs that pass this application's {@link IbanValidator} — used for
 * seeding demo data. Rather than reimplement the check-digit maths, it derives
 * the two check digits by testing candidates against the real validator, so the
 * output is guaranteed valid regardless of the validator's exact algorithm.
 */
public final class IbanGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final IbanValidator VALIDATOR = new IbanValidator();

    private IbanGenerator() {
    }

    public static String generate() {
        String country = "DE";
        StringBuilder bban = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            bban.append(RANDOM.nextInt(10));
        }
        for (int check = 2; check <= 98; check++) {
            String iban = country + String.format("%02d", check) + bban;
            if (VALIDATOR.isValid(iban, null)) {
                return iban;
            }
        }
        // Exactly one check-digit value is valid for a given BBAN; this is unreachable.
        throw new IllegalStateException("Could not derive valid IBAN check digits");
    }
}
