package com.scai.customer_portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scai.customer_portal.api.dto.ImportJiraRequest;
import com.scai.customer_portal.api.dto.IssuePatchRequest;
import com.scai.customer_portal.api.dto.IssueResponse;
import com.scai.customer_portal.domain.AppUser;
import com.scai.customer_portal.domain.Issue;
import com.scai.customer_portal.domain.Organization;
import com.scai.customer_portal.config.DefaultInternalOrganizationBootstrap;
import com.scai.customer_portal.domain.Pod;
import com.scai.customer_portal.domain.PortalRole;
import com.scai.customer_portal.repository.AppUserRepository;
import com.scai.customer_portal.repository.IssueRepository;
import com.scai.customer_portal.repository.OrganizationRepository;
import com.scai.customer_portal.repository.PodRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
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
	private final PodRepository podRepository;
	private final AppUserRepository appUserRepository;
	private final JiraRemoteService jiraRemoteService;
	private final JiraIssueMapper jiraIssueMapper;
	private final CurrentUserService currentUserService;
	private final ObjectMapper objectMapper;

	public IssueService(
			IssueRepository issueRepository,
			OrganizationRepository organizationRepository,
			PodRepository podRepository,
			AppUserRepository appUserRepository,
			JiraRemoteService jiraRemoteService,
			JiraIssueMapper jiraIssueMapper,
			CurrentUserService currentUserService,
			ObjectMapper objectMapper) {
		this.issueRepository = issueRepository;
		this.organizationRepository = organizationRepository;
		this.podRepository = podRepository;
		this.appUserRepository = appUserRepository;
		this.jiraRemoteService = jiraRemoteService;
		this.jiraIssueMapper = jiraIssueMapper;
		this.currentUserService = currentUserService;
		this.objectMapper = objectMapper;
	}

	public boolean isJiraIntegrationConfigured() {
		return jiraRemoteService.isConfigured();
	}

	@Transactional
	public IssueResponse importFromJira(ImportJiraRequest request) {
		AppUser actor = currentUserService.requireCurrentUser();
		if (actor.getRoles().stream().noneMatch(IMPORT_ROLES::contains)) {
			throw new SecurityException("Not allowed to import Jira issues");
		}
		JsonNode root = jiraRemoteService.fetchIssue(request.jiraKey());
		return applyJiraRootForUser(actor, root);
	}

	/**
	 * Re-fetches the issue from Jira and overwrites portal fields so data matches Jira (status, customer, description, etc.).
	 * Same permission rules as import; user must already be allowed to see this issue.
	 */
	@Transactional
	public IssueResponse syncIssueFromJira(UUID issueId) {
		AppUser actor = currentUserService.requireCurrentUser();
		if (actor.getRoles().stream().noneMatch(IMPORT_ROLES::contains)) {
			throw new SecurityException("Not allowed to sync Jira issues");
		}
		Issue issue = issueRepository.findById(issueId)
				.orElseThrow(() -> new IllegalArgumentException("Issue not found"));
		if (!canView(actor, issue)) {
			throw new SecurityException("Not allowed to sync this issue");
		}
		String key = issue.getJiraIssueKey();
		if (key == null || key.isBlank()) {
			throw new IllegalArgumentException("Issue is not linked to Jira");
		}
		JsonNode root = jiraRemoteService.fetchIssue(key);
		return applyJiraRootForUser(actor, root, issue);
	}

	/**
	 * Background job: refresh one issue from Jira using internal org resolution (Customer field → org, else 3SC).
	 * Does not change {@link Issue#getCreatedBy()}.
	 */
	@Transactional
	public void syncImportedIssueFromJiraForBackground(UUID issueId) {
		if (!jiraRemoteService.isConfigured()) {
			return;
		}
		Issue issue = issueRepository.findById(issueId).orElse(null);
		if (issue == null) {
			return;
		}
		String key = issue.getJiraIssueKey();
		if (key == null || key.isBlank()) {
			return;
		}
		JsonNode root = jiraRemoteService.fetchIssue(key);
		String snapshot = serializeJiraSnapshot(root);
		JsonNode fields = root.path("fields");
		Organization org = resolveOrganizationFromJiraInternal(fields);
		jiraIssueMapper.applyJsonToIssue(issue, root, org, null, snapshot);
		applyPodFromJira(fields, issue, null);
		issue.setAssignee(resolvePortalUserByEmail(JiraIssueMapper.jiraUserEmail(fields, "assignee")));
		issue.setPortalReporter(resolvePortalUserByEmail(issue.getJiraReporterEmail()));
		issueRepository.save(issue);
	}

	private IssueResponse applyJiraRootForUser(AppUser actor, JsonNode root) {
		String key = root.path("key").asText(null);
		if (key == null || key.isBlank()) {
			throw new IllegalStateException("Jira response did not contain issue key");
		}
		Issue issue = issueRepository.findByJiraIssueKey(key).orElse(new Issue());
		return applyJiraRootForUser(actor, root, issue);
	}

	private IssueResponse applyJiraRootForUser(AppUser actor, JsonNode root, Issue issue) {
		String snapshot = serializeJiraSnapshot(root);
		JsonNode fields = root.path("fields");
		Organization org = resolveOrganizationForImport(fields, actor);
		jiraIssueMapper.applyJsonToIssue(issue, root, org, actor, snapshot);
		applyPodFromJira(fields, issue, actor);
		issue.setAssignee(resolvePortalUserByEmail(JiraIssueMapper.jiraUserEmail(fields, "assignee")));
		issue.setPortalReporter(resolvePortalUserByEmail(issue.getJiraReporterEmail()));
		if (issue.getCreatedBy() == null && actor != null) {
			issue.setCreatedBy(actor);
		}
		return toResponse(issueRepository.save(issue));
	}

	private String serializeJiraSnapshot(JsonNode root) {
		try {
			return objectMapper.writeValueAsString(root);
		}
		catch (Exception e) {
			return root.toString();
		}
	}

	/**
	 * Customer users: always their org. Internal users: Jira "Customer" field (auto-discovered via /field API) →
	 * find or create {@link Organization}; if empty → internal org {@link DefaultInternalOrganizationBootstrap#INTERNAL_ORG_NAME}.
	 */
	private Organization resolveOrganizationForImport(JsonNode fields, AppUser actor) {
		if (actor != null && (actor.getRoles().contains(PortalRole.CUSTOMER_ADMIN)
				|| actor.getRoles().contains(PortalRole.CUSTOMER_USER))) {
			if (actor.getOrganization() == null) {
				throw new IllegalStateException("Customer user must belong to an organization");
			}
			return actor.getOrganization();
		}
		return resolveOrganizationFromJiraInternal(fields);
	}

	private Organization resolveOrganizationFromJiraInternal(JsonNode fields) {
		String customerLabel = jiraRemoteService.extractCustomerValueFromFields(fields);
		if (customerLabel != null && !customerLabel.isBlank()) {
			return getOrCreateOrganizationByName(customerLabel.trim());
		}
		return ensureInternalDefaultOrganization();
	}

	private Organization getOrCreateOrganizationByName(String name) {
		return organizationRepository.findByNameIgnoreCase(name)
				.orElseGet(() -> {
					try {
						return organizationRepository.save(Organization.builder().name(name).build());
					}
					catch (DataIntegrityViolationException e) {
						return organizationRepository.findByNameIgnoreCase(name)
								.orElseThrow(() -> e);
					}
				});
	}

	private Organization ensureInternalDefaultOrganization() {
		return organizationRepository.findByNameIgnoreCase(DefaultInternalOrganizationBootstrap.INTERNAL_ORG_NAME)
				.orElseGet(() -> {
					try {
						return organizationRepository.save(
								Organization.builder().name(DefaultInternalOrganizationBootstrap.INTERNAL_ORG_NAME).build());
					}
					catch (DataIntegrityViolationException e) {
						return organizationRepository.findByNameIgnoreCase(
										DefaultInternalOrganizationBootstrap.INTERNAL_ORG_NAME)
								.orElseThrow(() -> e);
					}
				});
	}

	/**
	 * Sets {@link Issue#getPod()} from Jira when possible, else keeps internal users aligned with their own pod
	 * so SC_LEAD/SC_AGENT do not lose visibility on re-import.
	 */
	private void applyPodFromJira(JsonNode fields, Issue issue, AppUser actor) {
		Pod fromJira = resolvePodEntityFromJira(fields, issue);
		if (actor == null || actor.getRoles().contains(PortalRole.SC_ADMIN)) {
			issue.setPod(fromJira);
			return;
		}
		if (actor.getRoles().contains(PortalRole.SC_LEAD) || actor.getRoles().contains(PortalRole.SC_AGENT)) {
			Pod actorPod = actor.getPod();
			if (fromJira != null && actorPod != null && fromJira.getId().equals(actorPod.getId())) {
				issue.setPod(fromJira);
			}
			else if (actorPod != null) {
				issue.setPod(actorPod);
			}
			else {
				issue.setPod(fromJira);
			}
			return;
		}
		issue.setPod(null);
	}

	/**
	 * 1) Optional {@code jira.pod-field-id} value → {@link PodRepository#findByNameIgnoreCase}.
	 * 2) Else {@link Issue#getModule()} (exact or first comma-separated token) → same lookup.
	 */
	private Pod resolvePodEntityFromJira(JsonNode fields, Issue issue) {
		String podLabel = jiraRemoteService.extractPodLabelFromFields(fields);
		if (podLabel != null && !podLabel.isBlank()) {
			Optional<Pod> byField = podRepository.findByNameIgnoreCase(podLabel.trim());
			if (byField.isPresent()) {
				return byField.get();
			}
		}
		String mod = issue.getModule();
		if (mod == null || mod.isBlank()) {
			return null;
		}
		String trimmed = mod.trim();
		Optional<Pod> full = podRepository.findByNameIgnoreCase(trimmed);
		if (full.isPresent()) {
			return full.get();
		}
		String first = trimmed.split(",")[0].trim();
		if (!first.isEmpty() && !first.equalsIgnoreCase(trimmed)) {
			return podRepository.findByNameIgnoreCase(first).orElse(null);
		}
		return null;
	}

	private AppUser resolvePortalUserByEmail(String email) {
		if (email == null || email.isBlank()) {
			return null;
		}
		return appUserRepository.findByEmailIgnoreCase(email.trim()).orElse(null);
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
			if (issue.getCreatedBy().getId().equals(actor.getId())) {
				return true;
			}
			if (issue.getAssignee() != null && issue.getAssignee().getId().equals(actor.getId())) {
				return true;
			}
			// Register flow creates CUSTOMER_USER; Jira reporter is rarely createdBy/assignee on import.
			return jiraReporterMatchesPortalUser(actor, issue);
		}
		if (actor.getRoles().contains(PortalRole.SC_LEAD)) {
			if (actor.getPod() == null) {
				return false;
			}
			return issue.getPod() != null && issue.getPod().getId().equals(actor.getPod().getId());
		}
		if (actor.getRoles().contains(PortalRole.SC_AGENT)) {
			if (issue.getPortalReporter() != null && issue.getPortalReporter().getId().equals(actor.getId())) {
				return true;
			}
			return jiraReporterMatchesPortalUser(actor, issue);
		}
		return false;
	}

	/** True when the logged-in user's email matches Jira {@link Issue#getJiraReporterEmail()} (case-insensitive). */
	private static boolean jiraReporterMatchesPortalUser(AppUser actor, Issue issue) {
		if (actor.getEmail() == null || actor.getEmail().isBlank()) {
			return false;
		}
		String jr = issue.getJiraReporterEmail();
		if (jr == null || jr.isBlank()) {
			return false;
		}
		return jr.trim().equalsIgnoreCase(actor.getEmail().trim());
	}

	@Transactional
	public IssueResponse patchIssue(UUID issueId, IssuePatchRequest request) {
		AppUser actor = currentUserService.requireCurrentUser();
		Issue issue = issueRepository.findById(issueId)
				.orElseThrow(() -> new IllegalArgumentException("Issue not found"));
		if (!canPatch(actor, issue)) {
			throw new SecurityException("Not allowed to update this issue");
		}
		if (request.organizationId() != null) {
			Organization o = organizationRepository.findById(request.organizationId())
					.orElseThrow(() -> new IllegalArgumentException("Organization not found"));
			issue.setOrganization(o);
		}
		if (Boolean.TRUE.equals(request.unassignAssignee())) {
			issue.setAssignee(null);
		}
		else if (request.assigneeId() != null) {
			AppUser u = appUserRepository.findById(request.assigneeId())
					.orElseThrow(() -> new IllegalArgumentException("Assignee user not found"));
			issue.setAssignee(u);
		}
		if (Boolean.TRUE.equals(request.clearPod()) || request.podId() != null) {
			if (!canMutateIssuePod(actor)) {
				throw new SecurityException("Not allowed to change pod on this issue");
			}
			if (Boolean.TRUE.equals(request.clearPod())) {
				issue.setPod(null);
			}
			else {
				Pod p = podRepository.findById(request.podId())
						.orElseThrow(() -> new IllegalArgumentException("Pod not found"));
				assertPodPatchAllowed(actor, p);
				issue.setPod(p);
			}
		}
		if (Boolean.TRUE.equals(request.clearPortalReporter())) {
			issue.setPortalReporter(null);
		}
		else if (request.portalReporterUserId() != null) {
			AppUser rep = appUserRepository.findById(request.portalReporterUserId())
					.orElseThrow(() -> new IllegalArgumentException("Reporter user not found"));
			issue.setPortalReporter(rep);
		}
		if (Boolean.TRUE.equals(request.clearRcaDescription())) {
			issue.setRcaDescription(null);
		}
		else if (request.rcaDescription() != null) {
			issue.setRcaDescription(request.rcaDescription());
		}
		if (request.portalStatus() != null) {
			issue.setPortalStatus(request.portalStatus());
		}
		if (request.module() != null) {
			issue.setModule(blankToNull(request.module()));
		}
		if (request.environment() != null) {
			issue.setEnvironment(blankToNull(request.environment()));
		}
		if (request.category() != null) {
			issue.setCategory(blankToNull(request.category()));
		}
		return toResponse(issueRepository.save(issue));
	}

	private static String blankToNull(String s) {
		if (s == null) {
			return null;
		}
		String t = s.trim();
		return t.isEmpty() ? null : t;
	}

	private static boolean canMutateIssuePod(AppUser actor) {
		return actor.getRoles().contains(PortalRole.SC_ADMIN)
				|| actor.getRoles().contains(PortalRole.SC_LEAD)
				|| actor.getRoles().contains(PortalRole.SC_AGENT);
	}

	/** Pod reassignment: admins any pod; SC_LEAD/SC_AGENT only their pod. */
	private static void assertPodPatchAllowed(AppUser actor, Pod targetPod) {
		if (actor.getRoles().contains(PortalRole.SC_ADMIN)) {
			return;
		}
		if (actor.getRoles().contains(PortalRole.SC_LEAD) || actor.getRoles().contains(PortalRole.SC_AGENT)) {
			Pod ap = actor.getPod();
			if (ap == null || !ap.getId().equals(targetPod.getId())) {
				throw new SecurityException("You can only assign issues to your own pod");
			}
			return;
		}
		throw new SecurityException("Not allowed to set pod on this issue");
	}

	private boolean canPatch(AppUser actor, Issue issue) {
		if (actor.getRoles().contains(PortalRole.SC_ADMIN)) {
			return true;
		}
		if (actor.getRoles().contains(PortalRole.SC_LEAD)) {
			return canView(actor, issue);
		}
		if (actor.getRoles().contains(PortalRole.SC_AGENT)) {
			if (issue.getPortalReporter() != null && issue.getPortalReporter().getId().equals(actor.getId())) {
				return true;
			}
			return jiraReporterMatchesPortalUser(actor, issue);
		}
		if (actor.getRoles().contains(PortalRole.CUSTOMER_ADMIN)) {
			return actor.getOrganization() != null
					&& issue.getOrganization().getId().equals(actor.getOrganization().getId());
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
				i.getPortalReporter() != null ? i.getPortalReporter().getId() : null,
				i.getPortalReporter() != null ? i.getPortalReporter().getEmail() : null,
				i.getJiraReporterEmail(),
				i.getJiraReporterDisplayName(),
				i.getCreatedBy().getId(),
				i.getCreatedBy().getEmail(),
				i.getLastSyncedAt());
	}
}
