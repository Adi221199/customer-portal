package com.scai.customer_portal.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityBeansConfig {

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(12);
	}

	@Bean
	public JwtEncoder jwtEncoder(JwtProperties jwtProperties) {
		byte[] secretBytes = requireSecret(jwtProperties.secret());
		SecretKey key = new SecretKeySpec(secretBytes, "HmacSHA256");
		return new NimbusJwtEncoder(new ImmutableSecret<>(key.getEncoded()));
	}

	@Bean
	public JwtDecoder jwtDecoder(JwtProperties jwtProperties) {
		byte[] secretBytes = requireSecret(jwtProperties.secret());
		SecretKeySpec secretKey = new SecretKeySpec(secretBytes, "HmacSHA256");
		return NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
	}

	private static byte[] requireSecret(String secret) {
		if (secret == null || secret.isBlank()) {
			throw new IllegalStateException("jwt.secret must be set (min 32 characters recommended)");
		}
		byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < 32) {
			throw new IllegalStateException("jwt.secret must be at least 32 bytes for HS256");
		}
		return bytes;
	}
}
