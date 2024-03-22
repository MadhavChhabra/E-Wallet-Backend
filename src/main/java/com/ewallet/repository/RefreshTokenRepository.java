package com.ewallet.repository;

import com.ewallet.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
	Optional<RefreshToken> findByRefreshToken(String refreshToken);

	boolean existsByRefreshToken(String refreshToken);

	@Query("SELECT rt.user.username FROM RefreshToken rt WHERE rt.refreshToken = :refreshToken")
	String getUsernameFromRefreshToken(String refreshToken);
}

