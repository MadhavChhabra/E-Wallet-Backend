package com.ewallet.repository;

import com.ewallet.model.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {

    Optional<BankAccount> findByiban(String iban);

    List<BankAccount> findByUserId(Long userId);

    boolean existsByibanIgnoreCase(String iban);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    BankAccount getReferenceByiban(String iban);
}
