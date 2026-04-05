package com.scai.customer_portal.service;

import com.scai.customer_portal.config.JwtProperties;
import com.scai.customer_portal.domain.AppUser;
import com.scai.customer_portal.domain.PortalRole;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class JwtTokenService {

	private final JwtEncoder jwtEncoder;
	private final JwtProperties jwtProperties;

	public JwtTokenService(JwtEncoder jwtEncoder, JwtProperties jwtProperties) {
		this.jwtEncoder = jwtEncoder;
		this.jwtProperties = jwtProperties;
	}

	public String createAccessToken(AppUser user) {
		Instant now = Instant.now();
		Instant exp = now.plusSeconds(jwtProperties.expirationSeconds());
		List<String> roles = user.getRoles().stream().map(PortalRole::name).sorted().collect(Collectors.toList());
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
				.issuer("customer-portal")
				.issuedAt(now)
				.expiresAt(exp)
				.subject(user.getId().toString())
				.claim("email", user.getEmail())
				.claim("roles", roles);
		if (user.getOrganization() != null) {
			claims.claim("organizationId", user.getOrganization().getId().toString());
		}
		if (user.getPods() != null && !user.getPods().isEmpty()) {
			List<String> podIds = user.getPods().stream().map(p -> p.getId().toString()).sorted().toList();
			claims.claim("podIds", podIds);
		}
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return this.jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
	}
}
