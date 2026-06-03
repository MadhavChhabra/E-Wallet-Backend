package com.ewallet.model;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@EqualsAndHashCode(of = {"iban"})
@Table(indexes = {
        @Index(name = "bank_account_user_id_iban_key", columnList = "user_id, iban", unique = true),
        @Index(name = "bank_account_user_id_name_key", columnList = "user_id, name", unique = true)
})
public class BankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(length = 34, nullable = false, unique = true)
    private String iban;

    @Column(length = 50, nullable = false)
    private String name;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "fromBankAccount", cascade = CascadeType.ALL)
    private Set<Transaction> fromTransactions = new HashSet<>();

    @OneToMany(mappedBy = "toBankAccount", cascade = CascadeType.ALL)
    private Set<Transaction> toTransactions = new HashSet<>();
}
