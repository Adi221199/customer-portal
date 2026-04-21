package com.scai.customer_portal.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	private final org.springframework.core.convert.converter.Converter<
			org.springframework.security.oauth2.jwt.Jwt,
			org.springframework.security.authentication.AbstractAuthenticationToken> jwtAuthConverter;
	private final List<String> corsAllowedOriginPatterns;

	public SecurityConfig(
			org.springframework.core.convert.converter.Converter<
					org.springframework.security.oauth2.jwt.Jwt,
					org.springframework.security.authentication.AbstractAuthenticationToken> jwtAuthConverter,
			@Value("${customer-portal.cors.allowed-origin-patterns}") String corsAllowedOriginPatternsRaw) {
		this.jwtAuthConverter = jwtAuthConverter;
		this.corsAllowedOriginPatterns = Arrays.stream(corsAllowedOriginPatternsRaw.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.toList();
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/", "/favicon.ico").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/auth/login", "/api/auth/register").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
						.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
						.requestMatchers("/api/admin/**").hasRole("SC_ADMIN")
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter)));
		return http.build();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOriginPatterns(corsAllowedOriginPatterns);
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("*"));
		config.setAllowCredentials(true);
		config.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}
}
