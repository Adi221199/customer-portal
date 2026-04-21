package com.scai.customer_portal.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One-time copy of legacy {@code app_user_pods} + {@code pods} names into {@code app_user_modules} when upgrading.
 */
@Component
@Order(0)
@Profile("!test")
public class LegacyPodToModuleAssignmentMigration implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(LegacyPodToModuleAssignmentMigration.class);

	private final JdbcTemplate jdbcTemplate;

	public LegacyPodToModuleAssignmentMigration(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void run(ApplicationArguments args) {
		try {
			Integer tables = jdbcTemplate.queryForObject("""
					select count(*) from information_schema.tables
					where table_schema = 'public' and table_name in ('app_user_pods', 'pods', 'app_user_modules')
					""", Integer.class);
			if (tables == null || tables < 3) {
				return;
			}
			Integer podJoinRows = jdbcTemplate.queryForObject(
					"select count(*) from app_user_pods", Integer.class);
			if (podJoinRows == null || podJoinRows == 0) {
				return;
			}
			int inserted = jdbcTemplate.update("""
					insert into app_user_modules (user_id, module_code)
					select distinct up.user_id, trim(both from p.name)
					from app_user_pods up
					inner join pods p on p.id = up.pod_id
					where not exists (
					  select 1 from app_user_modules x
					  where x.user_id = up.user_id
					    and lower(trim(both from x.module_code)) = lower(trim(both from p.name))
					)
					""");
			if (inserted > 0) {
				log.info("Migrated {} legacy pod assignment row(s) into app_user_modules.", inserted);
			}
		}
		catch (Exception e) {
			log.debug("Legacy pod→module migration skipped: {}", e.toString());
		}
	}
}
