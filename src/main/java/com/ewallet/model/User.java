package com.ewallet.model;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Entity
@EqualsAndHashCode(of = {"username"})
// Backtick-quoted so Hibernate emits a quoted identifier per dialect: `user` on
// MySQL (same table as before) and "user" on H2, where USER is a reserved word.
@Table(name = "`user`")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50, nullable = false)
    private String firstName;

    @Column(length = 50, nullable = false)
    private String lastName;

    @Column(length = 20, nullable = false, unique = true)
    private String username;

    @Column(length = 50, nullable = false, unique = true)
    private String email;

    @Column(length = 100, nullable = false)
    private String password;

    @Column(name = "profile_image_filename", length = 100)
    private String profileImageFilename;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private Set<BankAccount> bankAccounts = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<RefreshToken> refreshTokens;

    @ManyToMany(cascade = CascadeType.MERGE)
    @JoinTable(name = "user_role",
            joinColumns = @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false),
            inverseJoinColumns = @JoinColumn(name = "role_id", referencedColumnName = "id")
    )
    private Set<Role> roles = new HashSet<>();

    public void addBankAccount(BankAccount bankAccount) {
        bankAccounts.add(bankAccount);
        bankAccount.setUser(this);
    }

    public void removeBankAccount(BankAccount bankAccount) {
        bankAccounts.remove(bankAccount);
        bankAccount.setUser(null);
    }

    public void addRole(Role role) {
        roles.add(role);
        role.getUsers().add(this);
    }

    public void removeRole(Role role) {
        roles.remove(role);
        role.getUsers().remove(this);
    }
}
