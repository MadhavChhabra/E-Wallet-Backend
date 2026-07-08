package com.ewallet.repository;

import com.ewallet.model.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CardRepository extends JpaRepository<Card, Long> {

    List<Card> findByUserId(Long userId);

    boolean existsByUserIdAndLast4AndExpiryMonthAndExpiryYear(
            Long userId, String last4, int expiryMonth, int expiryYear);
}
