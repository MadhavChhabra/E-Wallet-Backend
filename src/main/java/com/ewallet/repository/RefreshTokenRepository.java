package com.ewallet.repository;

import com.ewallet.model.RefreshToken;
import com.ewallet.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	Optional<RefreshToken> findByRefreshToken(String refreshToken);

	boolean existsByRefreshToken(String refreshToken);

	@Query("SELECT rt.user.username FROM RefreshToken rt WHERE rt.refreshToken = :refreshToken")
	String getUsernameFromRefreshToken(String refreshToken);

	@Modifying
	@Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user = :user AND rt.revoked = false")
	int revokeAllForUser(@Param("user") User user);

	@Modifying
	@Query("DELETE FROM RefreshToken rt WHERE rt.revoked = true OR rt.expiresAt < :now")
	int deleteRevokedOrExpired(@Param("now") Instant now);
}
