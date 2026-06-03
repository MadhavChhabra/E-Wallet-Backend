package com.ewallet.repository;

import com.ewallet.model.BankAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {

    Optional<BankAccount> findByIban(String iban);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BankAccount b WHERE b.iban = :iban")
    Optional<BankAccount> findByIbanForUpdate(@Param("iban") String iban);

    List<BankAccount> findByUserId(Long userId);

    Page<BankAccount> findByUserId(Long userId, Pageable pageable);

    boolean existsByIbanIgnoreCase(String iban);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    @EntityGraph(attributePaths = {"user"})
    @Query("SELECT b FROM BankAccount b WHERE b.id = :id")
    Optional<BankAccount> findByIdWithUser(@Param("id") Long id);

    @EntityGraph(attributePaths = {"user"})
    @Query("SELECT b FROM BankAccount b WHERE b.iban = :iban")
    Optional<BankAccount> findByIbanWithUser(@Param("iban") String iban);
}
