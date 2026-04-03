package com.scai.customer_portal.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Component
public class PortalJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		Collection<GrantedAuthority> authorities = new ArrayList<>();
		Object rolesClaim = jwt.getClaim("roles");
		if (rolesClaim instanceof Collection<?> raw) {
			for (Object r : raw) {
				if (r != null) {
					authorities.add(new SimpleGrantedAuthority("ROLE_" + r.toString()));
				}
			}
		}
		else if (rolesClaim instanceof String s && !s.isBlank()) {
			authorities.add(new SimpleGrantedAuthority("ROLE_" + s));
		}
		return new JwtAuthenticationToken(jwt, List.copyOf(authorities));
	}
}
