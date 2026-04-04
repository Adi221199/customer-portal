package com.scai.customer_portal.config;

import com.scai.customer_portal.domain.AppUser;
import com.scai.customer_portal.domain.Organization;
import com.scai.customer_portal.domain.Pod;
import com.scai.customer_portal.domain.PortalRole;
import com.scai.customer_portal.repository.AppUserRepository;
import com.scai.customer_portal.repository.OrganizationRepository;
import com.scai.customer_portal.repository.PodRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Component
@Profile("!test")
public class DataSeed implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DataSeed.class);

	private final AppUserRepository appUserRepository;
	private final OrganizationRepository organizationRepository;
	private final PodRepository podRepository;
	private final PasswordEncoder passwordEncoder;

	public DataSeed(
			AppUserRepository appUserRepository,
			OrganizationRepository organizationRepository,
			PodRepository podRepository,
			PasswordEncoder passwordEncoder) {
		this.appUserRepository = appUserRepository;
		this.organizationRepository = organizationRepository;
		this.podRepository = podRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (appUserRepository.count() > 0) {
			return;
		}
		organizationRepository.save(Organization.builder().name(DefaultInternalOrganizationBootstrap.INTERNAL_ORG_NAME).build());
		Organization acme = organizationRepository.save(Organization.builder().name("Acme Corp").build());
		organizationRepository.save(Organization.builder().name("Globex").build());
		Pod pod = podRepository.save(Pod.builder().name("Delivery Pod 1").build());

		AppUser admin = AppUser.builder()
				.email("admin@3sc.local")
				.passwordHash(passwordEncoder.encode("ChangeMeAdmin!12"))
				.displayName("3SC Admin")
				.enabled(true)
				.organization(null)
				.pod(null)
				.roles(Set.of(PortalRole.SC_ADMIN))
				.build();
		appUserRepository.save(admin);

		AppUser lead = AppUser.builder()
				.email("lead@3sc.local")
				.passwordHash(passwordEncoder.encode("ChangeMeLead!12"))
				.displayName("3SC Lead")
				.enabled(true)
				.organization(null)
				.pod(pod)
				.roles(Set.of(PortalRole.SC_LEAD))
				.build();
		appUserRepository.save(lead);

		AppUser custAdmin = AppUser.builder()
				.email("customer.admin@acme.test")
				.passwordHash(passwordEncoder.encode("ChangeMeCust!12"))
				.displayName("Acme Admin")
				.enabled(true)
				.organization(acme)
				.pod(null)
				.roles(Set.of(PortalRole.CUSTOMER_ADMIN))
				.build();
		appUserRepository.save(custAdmin);

		log.info("Seeded demo organizations, pod, and users (first run only). Use documented default passwords only in local/dev.");
	}
}
