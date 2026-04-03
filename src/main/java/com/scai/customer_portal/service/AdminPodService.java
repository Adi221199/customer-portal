package com.scai.customer_portal.service;

import com.scai.customer_portal.api.dto.CreatePodRequest;
import com.scai.customer_portal.api.dto.PodResponse;
import com.scai.customer_portal.domain.Pod;
import com.scai.customer_portal.repository.PodRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminPodService {

	private final PodRepository podRepository;

	public AdminPodService(PodRepository podRepository) {
		this.podRepository = podRepository;
	}

	@Transactional(readOnly = true)
	public List<PodResponse> listPods() {
		return podRepository.findAll().stream()
				.map(p -> new PodResponse(p.getId(), p.getName()))
				.toList();
	}

	@Transactional
	public PodResponse createPod(CreatePodRequest request) {
		String name = request.name().trim();
		if (podRepository.existsByNameIgnoreCase(name)) {
			throw new IllegalArgumentException("A pod with this name already exists: " + name);
		}
		Pod saved = podRepository.save(Pod.builder().name(name).build());
		return new PodResponse(saved.getId(), saved.getName());
	}
}
