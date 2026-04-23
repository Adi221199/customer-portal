package com.scai.customer_portal.repository;

import com.scai.customer_portal.domain.Issue;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.lang.NonNull;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IssueRepository extends JpaRepository<Issue, UUID>, JpaSpecificationExecutor<Issue> {

	Optional<Issue> findByJiraIssueKey(String jiraIssueKey);

	Optional<Issue> findByPortalReference(String portalReference);

	@Query("select i.id from Issue i where i.jiraIssueKey is not null")
	List<UUID> findAllIdsWithJiraIssueKey();

	@EntityGraph(attributePaths = { "organization", "assignee", "createdBy", "portalReporter" })
	@Override
	@NonNull
	List<Issue> findAll();

	@EntityGraph(attributePaths = { "organization", "assignee", "createdBy", "portalReporter" })
	@Override
	@NonNull
	List<Issue> findAll(@NonNull Specification<Issue> spec);

	@EntityGraph(attributePaths = { "organization", "assignee", "createdBy", "portalReporter" })
	@Override
	Optional<Issue> findById(UUID id);

	List<Issue> findByOrganization_Id(UUID organizationId);

	List<Issue> findByAssignee_Id(UUID assigneeId);

	List<Issue> findByCreatedBy_Id(UUID createdById);

	/**
	 * Distinct non-blank environment values. Sorting is done in Java — PostgreSQL rejects
	 * {@code SELECT DISTINCT ... ORDER BY lower(trim(...))} when the ORDER BY expression is not in the select list.
	 */
	@Query("select distinct i.environment from Issue i where i.environment is not null and length(trim(i.environment)) > 0")
	List<String> findDistinctNonBlankEnvironments();

	/**
	 * Distinct module / project codes from stored {@code module} or Jira key prefix (PostgreSQL {@code split_part}).
	 * Sort in Java after fetch.
	 */
	@Query(value = """
			select distinct upper(trim(both from x.m)) as m from (
			  select coalesce(nullif(trim(both from i.module), ''), split_part(i.jira_issue_key, '-', 1)) as m
			  from issues i
			  where i.jira_issue_key is not null and position('-' in i.jira_issue_key) > 0
			) x
			where x.m is not null and trim(both from x.m) <> ''
			""", nativeQuery = true)
	List<String> findDistinctIssueModuleCodesRaw();
}
