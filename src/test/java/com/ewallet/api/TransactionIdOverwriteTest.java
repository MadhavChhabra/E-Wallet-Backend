package com.ewallet.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Every transaction gets a fresh id; an id in the request body is never used. */
class TransactionIdOverwriteTest extends ApiTestSupport {

    @Test
    void anIdInATransferBodyCannotOverwriteSomeoneElsesTransaction() throws Exception {
        Session owner = demoSession();
        JsonNode theirs = transactions(owner).get(0);
        long theirId = theirs.path("id").asLong();
        BigDecimal theirAmount = theirs.path("amount").decimalValue();

        Session other = demoSession();
        JsonNode mine = accounts(other);
        Map<String, Object> transfer = new HashMap<>(Map.of(
                "fromBankAccountIban", mine.get(0).path("iban").asText(),
                "toBankAccountIban", mine.get(1).path("iban").asText(),
                "amount", 1,
                "typeId", 1));
        transfer.put("id", theirId);
        postJson("/bank-accounts/transfer", other, transfer).andExpect(status().isCreated());

        JsonNode stillTheirs = dataOf(mvc.perform(authed(get(API + "/transactions/" + theirId), owner))
                .andExpect(status().isOk()));
        assertThat(stillTheirs.path("amount").decimalValue()).isEqualByComparingTo(theirAmount);
    }
}
