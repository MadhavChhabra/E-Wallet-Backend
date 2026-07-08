package com.ewallet.repository;

import com.ewallet.model.PasswordResetToken;
import com.ewallet.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findTopByUserAndConsumedFalseOrderByCreatedAtDesc(User user);

    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.consumed = true WHERE t.user = :user AND t.consumed = false")
    int consumeAllForUser(@Param("user") User user);
}
