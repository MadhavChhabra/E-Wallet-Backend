package com.ewallet.api;

import com.ewallet.validator.IbanGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Money only moves through operations the server controls; request bodies cannot set it. */
class AccountMoneyHardeningTest extends ApiTestSupport {

    @Test
    void aNewAccountOpensAtZeroWhateverBalanceTheClientSends() throws Exception {
        Session s = signupAndLogin("secret123");

        postJson("/bank-accounts", s, Map.of(
                "name", "Rich account",
                "iban", IbanGenerator.generate(),
                "balance", 1_000_000,
                "userId", s.userId()))
                .andExpect(status().isCreated());

        JsonNode account = accounts(s).get(0);
        assertThat(account.path("balance").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void usersCannotCreditTheirOwnAccountFromNowhere() throws Exception {
        Session s = demoSession();
        String iban = accounts(s).get(0).path("iban").asText();

        postJson("/bank-accounts/add-funds", s, Map.of(
                "fromBankAccountIban", iban,
                "toBankAccountIban", iban,
                "amount", 50_000,
                "typeId", 2))
                .andExpect(status().isForbidden());
    }

    @Test
    void updatingAnAccountCanRenameItButNeverChangesItsBalance() throws Exception {
        Session s = demoSession();
        JsonNode account = accounts(s).get(0);
        BigDecimal before = account.path("balance").decimalValue();

        putJson("/bank-accounts/" + account.path("id").asLong(), s, Map.of(
                "name", "Renamed",
                "iban", account.path("iban").asText(),
                "balance", 999_999,
                "userId", s.userId()))
                .andExpect(status().isOk());

        JsonNode after = accounts(s).get(0);
        assertThat(after.path("name").asText()).isEqualTo("Renamed");
        assertThat(after.path("balance").decimalValue()).isEqualByComparingTo(before);
    }
}
