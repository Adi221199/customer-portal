package com.scai.customer_portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scai.customer_portal.api.dto.ImportJiraRequest;
import com.scai.customer_portal.api.dto.IssueResponse;
import com.scai.customer_portal.domain.AppUser;
import com.scai.customer_portal.domain.Issue;
import com.scai.customer_portal.domain.Organization;
import com.scai.customer_portal.domain.Pod;
import com.scai.customer_portal.domain.PortalRole;
import com.scai.customer_portal.repository.IssueRepository;
import com.scai.customer_portal.repository.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class IssueService {

	private static final Set<PortalRole> IMPORT_ROLES = EnumSet.of(
			PortalRole.SC_ADMIN,
			PortalRole.SC_LEAD,
			PortalRole.SC_AGENT,
			PortalRole.CUSTOMER_ADMIN,
			PortalRole.CUSTOMER_USER);

	private final IssueRepository issueRepository;
	private final OrganizationRepository organizationRepository;
	private final JiraRemoteService jiraRemoteService;
	private final JiraIssueMapper jiraIssueMapper;
	private final CurrentUserService currentUserService;
	private final ObjectMapper objectMapper;

	public IssueService(
			IssueRepository issueRepository,
			OrganizationRepository organizationRepository,
			JiraRemoteService jiraRemoteService,
			JiraIssueMapper jiraIssueMapper,
			CurrentUserService currentUserService,
			ObjectMapper objectMapper) {
		this.issueRepository = issueRepository;
		this.organizationRepository = organizationRepository;
		this.jiraRemoteService = jiraRemoteService;
		this.jiraIssueMapper = jiraIssueMapper;
		this.currentUserService = currentUserService;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public IssueResponse importFromJira(ImportJiraRequest request) {
		AppUser actor = currentUserService.requireCurrentUser();
		if (actor.getRoles().stream().noneMatch(IMPORT_ROLES::contains)) {
			throw new SecurityException("Not allowed to import Jira issues");
		}
		JsonNode root = jiraRemoteService.fetchIssue(request.jiraKey());
		String key = root.path("key").asText(null);
		if (key == null || key.isBlank()) {
			throw new IllegalStateException("Jira response did not contain issue key");
		}
		String snapshot;
		try {
			snapshot = objectMapper.writeValueAsString(root);
		}
		catch (Exception e) {
			snapshot = root.toString();
		}

		Organization org = resolveTargetOrganization(actor, request.organizationId());
		Pod pod = resolveTargetPod(actor);

		Issue issue = issueRepository.findByJiraIssueKey(key).orElse(new Issue());
		jiraIssueMapper.applyJsonToIssue(issue, root, org, pod, actor, snapshot);
		if (issue.getCreatedBy() == null) {
			issue.setCreatedBy(actor);
		}
		issue = issueRepository.save(issue);
		return toResponse(issue);
	}

	private Organization resolveTargetOrganization(AppUser actor, UUID requestedOrgId) {
		boolean internal = actor.getRoles().stream().anyMatch(r ->
				r == PortalRole.SC_ADMIN || r == PortalRole.SC_LEAD || r == PortalRole.SC_AGENT);
		boolean customer = actor.getRoles().stream().anyMatch(r ->
				r == PortalRole.CUSTOMER_ADMIN || r == PortalRole.CUSTOMER_USER);

		if (customer) {
			if (actor.getOrganization() == null) {
				throw new IllegalStateException("Customer user must belong to an organization");
			}
			return actor.getOrganization();
		}

		if (actor.getRoles().contains(PortalRole.SC_ADMIN)) {
			if (requestedOrgId == null) {
				throw new IllegalArgumentException("organizationId is required for this user");
			}
			return organizationRepository.findById(requestedOrgId)
					.orElseThrow(() -> new IllegalArgumentException("Organization not found"));
		}

		// SC_LEAD / SC_AGENT
		if (requestedOrgId == null) {
			throw new IllegalArgumentException("organizationId is required for 3SC lead/agent import");
		}
		Organization org = organizationRepository.findById(requestedOrgId)
				.orElseThrow(() -> new IllegalArgumentException("Organization not found"));
		if (internal && actor.getPod() != null) {
			// MVP: require imported issue pod to match actor pod (enforced by setting pod on issue)
			return org;
		}
		return org;
	}

	private Pod resolveTargetPod(AppUser actor) {
		if (actor.getRoles().contains(PortalRole.SC_ADMIN)) {
			return null;
		}
		if (actor.getRoles().stream().anyMatch(r -> r == PortalRole.SC_LEAD || r == PortalRole.SC_AGENT)) {
			return actor.getPod();
		}
		return null;
	}

	@Transactional(readOnly = true)
	public List<IssueResponse> listForCurrentUser() {
		AppUser actor = currentUserService.requireCurrentUser();
		List<Issue> all = issueRepository.findAll();
		List<Issue> filtered = new ArrayList<>();
		for (Issue issue : all) {
			if (canView(actor, issue)) {
				filtered.add(issue);
			}
		}
		return filtered.stream().map(this::toResponse).toList();
	}

	private boolean canView(AppUser actor, Issue issue) {
		if (actor.getRoles().contains(PortalRole.SC_ADMIN)) {
			return true;
		}
		if (actor.getRoles().contains(PortalRole.CUSTOMER_ADMIN)) {
			return actor.getOrganization() != null
					&& issue.getOrganization().getId().equals(actor.getOrganization().getId());
		}
		if (actor.getRoles().contains(PortalRole.CUSTOMER_USER)) {
			if (actor.getOrganization() == null) {
				return false;
			}
			if (!issue.getOrganization().getId().equals(actor.getOrganization().getId())) {
				return false;
			}
			return issue.getCreatedBy().getId().equals(actor.getId())
					|| (issue.getAssignee() != null && issue.getAssignee().getId().equals(actor.getId()));
		}
		if (actor.getRoles().contains(PortalRole.SC_LEAD)) {
			if (actor.getPod() == null) {
				return false;
			}
			return issue.getPod() != null && issue.getPod().getId().equals(actor.getPod().getId());
		}
		if (actor.getRoles().contains(PortalRole.SC_AGENT)) {
			if (issue.getAssignee() != null && issue.getAssignee().getId().equals(actor.getId())) {
				return true;
			}
			if (actor.getPod() != null && issue.getPod() != null
					&& issue.getPod().getId().equals(actor.getPod().getId())) {
				return true;
			}
			return false;
		}
		return false;
	}

	private IssueResponse toResponse(Issue i) {
		return new IssueResponse(
				i.getId(),
				i.getJiraIssueKey(),
				i.getJiraIssueId(),
				i.getTitle(),
				i.getDescription(),
				i.getIssueDate(),
				i.getClosingDate(),
				i.getModule(),
				i.getEnvironment(),
				i.getCategory(),
				i.getSeverity(),
				i.getRcaDescription(),
				i.getJiraStatus(),
				i.getPortalStatus(),
				i.getOrganization().getId(),
				i.getOrganization().getName(),
				i.getPod() != null ? i.getPod().getId() : null,
				i.getPod() != null ? i.getPod().getName() : null,
				i.getAssignee() != null ? i.getAssignee().getId() : null,
				i.getAssignee() != null ? i.getAssignee().getEmail() : null,
				i.getCreatedBy().getId(),
				i.getCreatedBy().getEmail(),
				i.getLastSyncedAt());
	}
}
