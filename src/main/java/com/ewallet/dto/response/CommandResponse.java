package com.ewallet.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * Data Transfer Object used for returning id value of the saved entity

 */
@Getter
@Builder

public class CommandResponse {
	private final Long id;
	private final String message; // Nullable message field

	// Constructor, getters, and other methods
}