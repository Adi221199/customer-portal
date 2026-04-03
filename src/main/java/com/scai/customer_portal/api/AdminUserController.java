package com.scai.customer_portal.api;

import com.scai.customer_portal.api.dto.AdminUserUpdateRequest;
import com.scai.customer_portal.api.dto.UserResponse;
import com.scai.customer_portal.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

	private final AdminUserService adminUserService;

	public AdminUserController(AdminUserService adminUserService) {
		this.adminUserService = adminUserService;
	}

	@GetMapping
	public List<UserResponse> list() {
		return adminUserService.listUsers();
	}

	@PatchMapping("/{id}")
	public UserResponse update(@PathVariable UUID id, @Valid @RequestBody AdminUserUpdateRequest request) {
		return adminUserService.updateUser(id, request);
	}
}
