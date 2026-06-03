package com.ewallet.repository;

import com.ewallet.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @EntityGraph(attributePaths = {"type", "fromBankAccount", "toBankAccount"})
    Optional<Transaction> findByReferenceNumber(UUID referenceNumber);

    @EntityGraph(attributePaths = {"type", "fromBankAccount", "toBankAccount"})
    @Override
    Optional<Transaction> findById(Long id);

    @EntityGraph(attributePaths = {"type", "fromBankAccount", "toBankAccount"})
    @Query("""
            SELECT t FROM Transaction t
            WHERE t.fromBankAccount.user.id = :userId
               OR t.toBankAccount.user.id = :userId
            ORDER BY t.createdAt DESC
            """)
    Page<Transaction> findAllByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            SELECT t FROM Transaction t
            JOIN FETCH t.fromBankAccount fa
            JOIN FETCH t.toBankAccount ta
            WHERE fa.id = :accountId OR ta.id = :accountId
            """)
    List<Transaction> findAllByAccountId(@Param("accountId") Long accountId);
}
