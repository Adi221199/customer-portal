package com.scai.customer_portal.api;

import com.scai.customer_portal.api.dto.CreatePodRequest;
import com.scai.customer_portal.api.dto.PodResponse;
import com.scai.customer_portal.service.AdminPodService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/pods")
public class AdminPodController {

	private final AdminPodService adminPodService;

	public AdminPodController(AdminPodService adminPodService) {
		this.adminPodService = adminPodService;
	}

	@GetMapping
	public List<PodResponse> list() {
		return adminPodService.listPods();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PodResponse create(@Valid @RequestBody CreatePodRequest request) {
		return adminPodService.createPod(request);
	}
}
