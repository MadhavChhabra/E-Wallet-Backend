package com.ewallet.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A transaction shows who the money went to or came from, but never the other person's balance,
 * email or roles. Your own accounts still show their balance.
 */
class TransactionPrivacyTest extends ApiTestSupport {

    @Test
    void theOtherPartyIsShownWithoutTheirBalanceOrEmail() throws Exception {
        Session sender = demoSession();
        Session receiver = demoSession();
        String from = accounts(sender).get(0).path("iban").asText();
        String to = accounts(receiver).get(0).path("iban").asText();

        postJson("/bank-accounts/transfer", sender, Map.of(
                "fromBankAccountIban", from,
                "toBankAccountIban", to,
                "amount", 10,
                "typeId", 1))
                .andExpect(status().isCreated());

        JsonNode sent = findByCounterparty(transactions(sender), "toBankAccount", to);
        assertPartyIsSlim(sent.path("toBankAccount"), receiver);

        JsonNode received = findByCounterparty(transactions(receiver), "fromBankAccount", from);
        assertPartyIsSlim(received.path("fromBankAccount"), sender);

        assertThat(accounts(sender).get(0).has("balance")).isTrue();
    }

    private static JsonNode findByCounterparty(JsonNode txs, String side, String iban) {
        for (JsonNode tx : txs) {
            if (iban.equals(tx.path(side).path("iban").asText())) {
                return tx;
            }
        }
        throw new AssertionError("no transaction with " + side + " " + iban);
    }

    private static void assertPartyIsSlim(JsonNode party, Session owner) {
        assertThat(party.has("balance")).as("counterparty balance").isFalse();
        JsonNode user = party.path("user");
        assertThat(user.has("email")).as("counterparty email").isFalse();
        assertThat(user.has("roles")).as("counterparty roles").isFalse();
        // What the app needs to label the row is still there.
        assertThat(user.path("id").asLong()).isEqualTo(owner.userId());
        assertThat(user.path("username").asText()).isEqualTo(owner.username());
        assertThat(party.path("iban").asText()).isNotBlank();
    }
}
