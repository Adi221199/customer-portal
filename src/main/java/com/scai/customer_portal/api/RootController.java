package com.scai.customer_portal.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class RootController {

	@GetMapping("/")
	public Map<String, Object> root() {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("service", "customer-portal");
		m.put("message", "API is up. Use JSON endpoints below (not a browser login page).");
		m.put("health", "/actuator/health");
		m.put("register", "POST /api/auth/register");
		m.put("login", "POST /api/auth/login");
		m.put("me", "GET /api/auth/me (Bearer token)");
		m.put("issues", "GET /api/issues; POST import-jira; POST {id}/sync-from-jira (Bearer)");
		m.put("dashboard", "GET /api/dashboard/meta|filters|charts/{path}|aggregate (Bearer)");
		m.put("chat", "POST /api/chat — NVIDIA NIM assistant + NL dashboard filters (Bearer)");
		return m;
	}
}
