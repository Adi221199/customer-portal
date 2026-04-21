package com.scai.customer_portal.config;

import com.scai.customer_portal.domain.AppUser;
import com.scai.customer_portal.domain.Organization;
import com.scai.customer_portal.domain.PortalRole;
import com.scai.customer_portal.repository.AppUserRepository;
import com.scai.customer_portal.repository.OrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * Demo users for local dev. Runs after {@link DefaultInternalOrganizationBootstrap} (internal {@code 3SC} org).
 */
@Component
@Order(2)
@Profile("!test")
public class DataSeed implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DataSeed.class);

	private final AppUserRepository appUserRepository;
	private final OrganizationRepository organizationRepository;
	private final PasswordEncoder passwordEncoder;

	public DataSeed(
			AppUserRepository appUserRepository,
			OrganizationRepository organizationRepository,
			PasswordEncoder passwordEncoder) {
		this.appUserRepository = appUserRepository;
		this.organizationRepository = organizationRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (appUserRepository.count() > 0) {
			return;
		}
		Organization internal = organizationRepository
				.findByNameIgnoreCase(DefaultInternalOrganizationBootstrap.INTERNAL_ORG_NAME)
				.orElseThrow(() -> new IllegalStateException(
						"Expected organization '" + DefaultInternalOrganizationBootstrap.INTERNAL_ORG_NAME
								+ "' from DefaultInternalOrganizationBootstrap"));

		AppUser admin = AppUser.builder()
				.email("admin@3sc.local")
				.passwordHash(passwordEncoder.encode("ChangeMeAdmin!12"))
				.displayName("3SC Admin")
				.enabled(true)
				.organization(null)
				.assignedModules(new HashSet<>())
				.roles(Set.of(PortalRole.SC_ADMIN))
				.build();
		appUserRepository.save(admin);

		AppUser lead = AppUser.builder()
				.email("lead@3sc.local")
				.passwordHash(passwordEncoder.encode("ChangeMeLead!12"))
				.displayName("3SC Lead")
				.enabled(true)
				.organization(null)
				.assignedModules(new HashSet<>(Set.of("EDM")))
				.roles(Set.of(PortalRole.SC_LEAD))
				.build();
		appUserRepository.save(lead);

		AppUser custAdmin = AppUser.builder()
				.email("customer.admin@3sc.local")
				.passwordHash(passwordEncoder.encode("ChangeMeCust!12"))
				.displayName("Customer Admin (3SC)")
				.enabled(true)
				.organization(internal)
				.assignedModules(new HashSet<>())
				.roles(Set.of(PortalRole.CUSTOMER_ADMIN))
				.build();
		appUserRepository.save(custAdmin);

		log.info("Seeded demo users (first run only). Use documented default passwords only in local/dev.");
	}
}
