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

	@Query("select i.id from Issue i where i.jiraIssueKey is not null")
	List<UUID> findAllIdsWithJiraIssueKey();

	@EntityGraph(attributePaths = { "organization", "pod", "assignee", "createdBy", "portalReporter" })
	@Override
	@NonNull
	List<Issue> findAll();

	@EntityGraph(attributePaths = { "organization", "pod", "assignee", "createdBy", "portalReporter" })
	@Override
	@NonNull
	List<Issue> findAll(@NonNull Specification<Issue> spec);

	@EntityGraph(attributePaths = { "organization", "pod", "assignee", "createdBy", "portalReporter" })
	@Override
	Optional<Issue> findById(UUID id);

	List<Issue> findByOrganization_Id(UUID organizationId);

	List<Issue> findByPod_Id(UUID podId);

	List<Issue> findByAssignee_Id(UUID assigneeId);

	List<Issue> findByCreatedBy_Id(UUID createdById);
}
