package com.scai.customer_portal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
		String secret,
		@DefaultValue("43200") long expirationSeconds
) {
}
