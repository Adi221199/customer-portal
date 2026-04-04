package com.scai.customer_portal.config;

import com.scai.customer_portal.domain.Organization;
import com.scai.customer_portal.repository.OrganizationRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures internal fallback org {@code 3SC} exists (tickets without Jira Customer).
 */
@Component
@Order(1)
@Profile("!test")
public class DefaultInternalOrganizationBootstrap implements ApplicationRunner {

	public static final String INTERNAL_ORG_NAME = "3SC";

	private final OrganizationRepository organizationRepository;

	public DefaultInternalOrganizationBootstrap(OrganizationRepository organizationRepository) {
		this.organizationRepository = organizationRepository;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (organizationRepository.findByNameIgnoreCase(INTERNAL_ORG_NAME).isPresent()) {
			return;
		}
		try {
			organizationRepository.save(Organization.builder().name(INTERNAL_ORG_NAME).build());
		}
		catch (DataIntegrityViolationException ignored) {
			// parallel startup or race
		}
	}
}
