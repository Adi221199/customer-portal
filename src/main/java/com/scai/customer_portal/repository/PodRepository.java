package com.scai.customer_portal.repository;

import com.scai.customer_portal.domain.Pod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PodRepository extends JpaRepository<Pod, UUID> {

	boolean existsByNameIgnoreCase(String name);

	Optional<Pod> findByNameIgnoreCase(String name);
}
