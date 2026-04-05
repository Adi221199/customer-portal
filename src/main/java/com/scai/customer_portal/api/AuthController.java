package com.scai.customer_portal.api;

import com.scai.customer_portal.api.dto.AuthResponse;
import com.scai.customer_portal.api.dto.LoginRequest;
import com.scai.customer_portal.api.dto.RegisterRequest;
import com.scai.customer_portal.api.dto.UserResponse;
import com.scai.customer_portal.service.AuthService;
import com.scai.customer_portal.service.CurrentUserService;
import com.scai.customer_portal.service.UserResponseMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;
	private final CurrentUserService currentUserService;
	private final UserResponseMapper userResponseMapper;

	public AuthController(
			AuthService authService,
			CurrentUserService currentUserService,
			UserResponseMapper userResponseMapper) {
		this.authService = authService;
		this.currentUserService = currentUserService;
		this.userResponseMapper = userResponseMapper;
	}

	@GetMapping("/register")
	public ResponseEntity<Map<String, String>> registerHint() {
		return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
				.header(HttpHeaders.ALLOW, "POST")
				.body(Map.of(
						"error", "Method Not Allowed",
						"hint", "Use POST /api/auth/register with JSON body: email, password, displayName; organizationName optional (customers fill it, internal users may omit)"));
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public UserResponse register(@Valid @RequestBody RegisterRequest request) {
		return authService.register(request);
	}

	@GetMapping("/login")
	public ResponseEntity<Map<String, String>> loginHint() {
		return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
				.header(HttpHeaders.ALLOW, "POST")
				.body(Map.of(
						"error", "Method Not Allowed",
						"hint", "Use POST /api/auth/login with JSON body: {\"email\":\"...\",\"password\":\"...\"}"));
	}

	@PostMapping("/login")
	public AuthResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}

	@GetMapping("/me")
	public UserResponse me() {
		return userResponseMapper.toResponse(currentUserService.requireCurrentUser());
	}
}
