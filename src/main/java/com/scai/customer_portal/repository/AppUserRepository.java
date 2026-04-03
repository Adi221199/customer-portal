package com.scai.customer_portal.repository;

import com.scai.customer_portal.domain.AppUser;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

	Optional<AppUser> findByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCase(String email);

	@EntityGraph(attributePaths = { "organization", "pod", "roles" })
	@Override
	List<AppUser> findAll();

	@EntityGraph(attributePaths = { "organization", "pod", "roles" })
	@Override
	Optional<AppUser> findById(UUID id);
}
