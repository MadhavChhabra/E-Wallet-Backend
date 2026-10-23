package com.ewallet.dto.response;

import lombok.Data;

/**
 * One side of a transaction as the other party may see it: enough to say who the money went to or
 * came from, never their balance, email or roles. Your own balances come from the account
 * endpoints, which check ownership.
 */
@Data
public class TransactionPartyResponse {

    private Long id;
    private String iban;
    private String name;
    private Owner user;

    @Data
    public static class Owner {
        private Long id;
        private String username;
        private String firstName;
        private String lastName;
    }
}
