package com.ewallet.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "REFRESH_TOKENS", indexes = {
        @Index(name = "idx_refresh_token_value", columnList = "REFRESH_TOKEN"),
        @Index(name = "idx_refresh_token_user", columnList = "user_id")
})
public class RefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY) // Use GenerationType.IDENTITY for MySQL
	private Long id;

	@Column(name = "REFRESH_TOKEN", nullable = false, length = 10000)
	private String refreshToken;

	@Column(name = "REVOKED")
	private boolean revoked;

	@Column(name = "EXPIRES_AT")
	private Instant expiresAt;

	@ManyToOne
	@JoinColumn(name = "user_id", referencedColumnName = "id")
	private User user;

	public boolean isExpired() {
		return expiresAt != null && Instant.now().isAfter(expiresAt);
	}
}
