package com.scai.customer_portal.api.dto;

public record AuthResponse(
		String accessToken,
		String tokenType,
		long expiresInSeconds
) {
	public static AuthResponse of(String token, long expiresInSeconds) {
		return new AuthResponse(token, "Bearer", expiresInSeconds);
	}
}
