package com.scai.customer_portal.repository;

import com.scai.customer_portal.domain.Issue;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IssueRepository extends JpaRepository<Issue, UUID> {

	Optional<Issue> findByJiraIssueKey(String jiraIssueKey);

	@EntityGraph(attributePaths = { "organization", "pod", "assignee", "createdBy" })
	@Override
	List<Issue> findAll();

	@EntityGraph(attributePaths = { "organization", "pod", "assignee", "createdBy" })
	@Override
	Optional<Issue> findById(UUID id);

	List<Issue> findByOrganization_Id(UUID organizationId);

	List<Issue> findByPod_Id(UUID podId);

	List<Issue> findByAssignee_Id(UUID assigneeId);

	List<Issue> findByCreatedBy_Id(UUID createdById);
}
