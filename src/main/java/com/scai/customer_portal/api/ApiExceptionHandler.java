package com.scai.customer_portal.api;

import com.scai.customer_portal.api.dto.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiError> badRequest(IllegalArgumentException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ApiError.of(400, "Bad Request", ex.getMessage()));
	}

	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<ApiError> illegalState(IllegalStateException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ApiError.of(400, "Bad Request", ex.getMessage()));
	}

	@ExceptionHandler(SecurityException.class)
	public ResponseEntity<ApiError> forbidden(SecurityException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(ApiError.of(403, "Forbidden", ex.getMessage()));
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiError> accessDenied(AccessDeniedException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(ApiError.of(403, "Forbidden", "Access denied"));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
		String msg = ex.getBindingResult().getFieldErrors().stream()
				.map(FieldError::getDefaultMessage)
				.collect(Collectors.joining("; "));
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ApiError.of(400, "Validation Error", msg));
	}
}
