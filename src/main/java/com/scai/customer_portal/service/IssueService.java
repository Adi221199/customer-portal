package com.scai.customer_portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scai.customer_portal.api.dto.BulkImportOptionsResponse;
import com.scai.customer_portal.api.dto.BulkJiraImportJobAcceptedResponse;
import com.scai.customer_portal.api.dto.BulkJiraImportJobStatusResponse;
import com.scai.customer_portal.api.dto.BulkJiraImportRequest;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Locale;
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

	/** Bulk import spans all Jira-matched orgs; only internal roles may run it. */
	private static final Set<PortalRole> BULK_IMPORT_ROLES = EnumSet.of(
			PortalRole.SC_ADMIN,
			PortalRole.SC_LEAD,
			PortalRole.SC_AGENT);

	private final IssueRepository issueRepository;
	private final OrganizationRepository organizationRepository;
	private final PodRepository podRepository;
	private final AppUserRepository appUserRepository;
	private final JiraRemoteService jiraRemoteService;
	private final JiraIssueMapper jiraIssueMapper;
	private final CurrentUserService currentUserService;
	private final ObjectMapper objectMapper;
	private final TransactionTemplate requiresNewTx;
	private final BulkJiraImportJobRegistry bulkJiraImportJobRegistry;
	private final TaskExecutor bulkImportExecutor;

	public IssueService(
			IssueRepository issueRepository,
			OrganizationRepository organizationRepository,
			PodRepository podRepository,
			AppUserRepository appUserRepository,
			JiraRemoteService jiraRemoteService,
			JiraIssueMapper jiraIssueMapper,
			CurrentUserService currentUserService,
			ObjectMapper objectMapper,
			PlatformTransactionManager transactionManager,
			BulkJiraImportJobRegistry bulkJiraImportJobRegistry,
			@Qualifier("bulkImportExecutor") TaskExecutor bulkImportExecutor) {
		this.issueRepository = issueRepository;
		this.organizationRepository = organizationRepository;
		this.podRepository = podRepository;
		this.appUserRepository = appUserRepository;
		this.jiraRemoteService = jiraRemoteService;
		this.jiraIssueMapper = jiraIssueMapper;
		this.currentUserService = currentUserService;
		this.objectMapper = objectMapper;
		this.requiresNewTx = new TransactionTemplate(transactionManager);
		this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.bulkJiraImportJobRegistry = bulkJiraImportJobRegistry;
		this.bulkImportExecutor = bulkImportExecutor;
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
	 * JQL search for issues where Customer and Environment are not empty, then full import per key (same mapping as
	 * single import). One transaction per issue so one failure does not roll back the whole run.
	 * Restricted to {@link #BULK_IMPORT_ROLES} (internal team only).
	 */
	public BulkImportOptionsResponse bulkImportOptions() {
		AppUser actor = currentUserService.requireCurrentUser();
		if (actor.getRoles().stream().noneMatch(BULK_IMPORT_ROLES::contains)) {
			throw new SecurityException("Not allowed to load bulk import options");
		}
		List<BulkImportOptionsResponse.NameLabelOption> orgs = organizationRepository.findAll().stream()
				.map(o -> new BulkImportOptionsResponse.NameLabelOption(o.getName(), o.getName()))
				.sorted(Comparator.comparing(BulkImportOptionsResponse.NameLabelOption::label))
				.toList();
		List<BulkImportOptionsResponse.NameLabelOption> pods = podRepository.findAll().stream()
				.map(p -> new BulkImportOptionsResponse.NameLabelOption(p.getName(), p.getName()))
				.sorted(Comparator.comparing(BulkImportOptionsResponse.NameLabelOption::label))
				.toList();
		List<String> envs = issueRepository.findDistinctNonBlankEnvironments().stream()
				.sorted(Comparator.comparing(s -> s.trim().toLowerCase(Locale.ROOT)))
				.toList();
		return new BulkImportOptionsResponse(orgs, pods, envs);
	}

	/**
	 * Queues a background Jira bulk import; poll {@link #getBulkImportJobStatus(UUID)} until status is COMPLETED or
	 * FAILED.
	 */
	public BulkJiraImportJobAcceptedResponse startBulkImportJob(BulkJiraImportRequest filter) {
		AppUser actor = currentUserService.requireCurrentUser();
		if (actor.getRoles().stream().noneMatch(BULK_IMPORT_ROLES::contains)) {
			throw new SecurityException("Not allowed to run bulk Jira import");
		}
		if (!jiraRemoteService.isConfigured()) {
			throw new IllegalStateException("Jira is not configured");
		}
		BulkJiraImportRequest f = filter != null ? filter : BulkJiraImportRequest.defaults();
		UUID jobId = bulkJiraImportJobRegistry.createJob(actor.getId());
		UUID actorId = actor.getId();
		bulkImportExecutor.execute(() -> executeBulkImportJob(jobId, actorId, f));
		return new BulkJiraImportJobAcceptedResponse(jobId, BulkJiraImportJobStatus.QUEUED.name());
	}

	public BulkJiraImportJobStatusResponse getBulkImportJobStatus(UUID jobId) {
		AppUser actor = currentUserService.requireCurrentUser();
		BulkJiraImportJobRegistry.Job job = bulkJiraImportJobRegistry.get(jobId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
		if (!job.ownerUserId.equals(actor.getId()) && !actor.getRoles().contains(PortalRole.SC_ADMIN)) {
			throw new SecurityException("Not allowed to view this job");
		}
		return bulkJiraImportJobRegistry.toResponse(job);
	}

	/**
	 * Runs off the HTTP thread; updates {@link BulkJiraImportJobRegistry} as work progresses.
	 */
	public void executeBulkImportJob(UUID jobId, UUID actorUserId, BulkJiraImportRequest filter) {
		try {
			bulkJiraImportJobRegistry.markRunning(jobId);
			AppUser actor = appUserRepository.findById(actorUserId).orElse(null);
			if (actor == null) {
				bulkJiraImportJobRegistry.markFailed(jobId, "User not found");
				return;
			}
			if (!jiraRemoteService.isConfigured()) {
				bulkJiraImportJobRegistry.markFailed(jobId, "Jira is not configured");
				return;
			}
			int maxTotal = jiraRemoteService.configuredBulkImportMaxTotal();
			List<String> keys;
			try {
				keys = jiraRemoteService.searchIssueKeysForBulkImport(filter, maxTotal);
			}
			catch (RuntimeException e) {
				bulkJiraImportJobRegistry.markFailed(jobId, e.getMessage() != null ? e.getMessage() : "Jira search failed");
				return;
			}
			bulkJiraImportJobRegistry.setSearchResult(jobId, keys.size());
			for (String key : keys) {
				try {
					requiresNewTx.executeWithoutResult(status -> {
						JsonNode root = jiraRemoteService.fetchIssue(key);
						applyJiraRootForUser(actor, root);
					});
					bulkJiraImportJobRegistry.recordSuccess(jobId);
				}
				catch (RuntimeException e) {
					bulkJiraImportJobRegistry.recordFailure(jobId, key, e.getMessage());
				}
			}
			bulkJiraImportJobRegistry.markCompleted(jobId);
		}
		catch (Exception e) {
			String msg = e.getMessage() != null ? e.getMessage() : "Unexpected error";
			bulkJiraImportJobRegistry.markFailed(jobId, msg);
		}
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
	 * Background job: refresh progress fields from Jira (status, summary when present, resolution date, priority).
	 * Does not overwrite organization, pod, env/module/category, RCA, reporter, assignee, description, etc.
	 * Does not change {@link Issue#getCreatedBy()}. Use {@link #syncIssueFromJira} for a full re-import.
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
		jiraIssueMapper.applyBackgroundProgressFromJira(issue, root, snapshot);
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
		applyAssigneeFromJira(fields, issue);
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
			var actorPods = actor.getPods();
			if (fromJira != null && actor.isAssignedToPod(fromJira)) {
				issue.setPod(fromJira);
			}
			else if (actorPods != null && actorPods.size() == 1) {
				issue.setPod(actorPods.iterator().next());
			}
			else if (actorPods == null || actorPods.isEmpty()) {
				issue.setPod(fromJira);
			}
			else {
				issue.setPod(null);
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

	private void applyAssigneeFromJira(JsonNode fields, Issue issue) {
		JsonNode assigneeNode = fields.get("assignee");
		if (assigneeNode == null || assigneeNode.isNull() || assigneeNode.isMissingNode()) {
			issue.setAssignee(null);
			issue.setJiraAssigneeAccountId(null);
			issue.setJiraAssigneeEmail(null);
			issue.setJiraAssigneeDisplayName(null);
			return;
		}
		String accountId = JiraIssueMapper.jiraUserAccountId(fields, "assignee");
		String displayName = JiraIssueMapper.jiraUserDisplayName(fields, "assignee");
		String emailFromField = JiraIssueMapper.jiraUserEmail(fields, "assignee");
		String email = emailFromField;
		if ((email == null || email.isBlank()) && accountId != null) {
			email = jiraRemoteService.lookupUserEmailByAccountId(accountId);
		}
		issue.setJiraAssigneeAccountId(accountId);
		issue.setJiraAssigneeDisplayName(displayName);
		issue.setJiraAssigneeEmail(email != null && !email.isBlank() ? email.trim() : null);
		issue.setAssignee(resolvePortalUserByEmail(email));
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
		return issueRepository.findAll(IssueVisibilitySpecification.visibleTo(actor)).stream()
				.sorted(Comparator.comparing(Issue::getLastSyncedAt, Comparator.nullsLast(Comparator.reverseOrder())))
				.map(this::toResponse)
				.toList();
	}

	private boolean canView(AppUser actor, Issue issue) {
		return issueRepository.exists(Specification.<Issue>where((root, q, cb) -> cb.equal(root.get("id"), issue.getId()))
				.and(IssueVisibilitySpecification.visibleTo(actor)));
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
		if (request.organizationName() != null && !request.organizationName().isBlank()) {
			applyOrganizationNameIfSlotOpen(issue, request.organizationName().trim());
		}
		if (request.podName() != null && !request.podName().isBlank() && issue.getPod() == null) {
			if (!canMutateIssuePod(actor)) {
				throw new SecurityException("Not allowed to change pod on this issue");
			}
			Pod p = podRepository.findByNameIgnoreCase(request.podName().trim())
					.orElseThrow(() -> new IllegalArgumentException("Pod not found: " + request.podName().trim()));
			assertPodPatchAllowed(actor, p);
			issue.setPod(p);
		}
		if (request.closingDate() != null && issue.getClosingDate() == null) {
			issue.setClosingDate(request.closingDate());
		}
		if (request.module() != null && isTextSlotEmpty(issue.getModule())) {
			issue.setModule(blankToNull(request.module()));
		}
		if (request.environment() != null && isTextSlotEmpty(issue.getEnvironment())) {
			issue.setEnvironment(blankToNull(request.environment()));
		}
		if (request.category() != null && isTextSlotEmpty(issue.getCategory())) {
			issue.setCategory(blankToNull(request.category()));
		}
		if (request.severity() != null && issue.getSeverity() == null) {
			issue.setSeverity(request.severity());
		}
		// RCA is narrative text: allow edits on every PATCH when the client sends the field (not gap-fill only).
		if (request.rcaDescription() != null) {
			issue.setRcaDescription(blankToNull(request.rcaDescription()));
		}
		return toResponse(issueRepository.save(issue));
	}

	private void applyOrganizationNameIfSlotOpen(Issue issue, String name) {
		Organization cur = issue.getOrganization();
		boolean open = cur == null
				|| DefaultInternalOrganizationBootstrap.INTERNAL_ORG_NAME.equalsIgnoreCase(cur.getName());
		if (!open) {
			return;
		}
		Organization next = organizationRepository.findByNameIgnoreCase(name)
				.orElseGet(() -> organizationRepository.save(Organization.builder().name(name).build()));
		issue.setOrganization(next);
	}

	private static boolean isTextSlotEmpty(String s) {
		return s == null || s.isBlank();
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
			if (!actor.isAssignedToPod(targetPod)) {
				throw new SecurityException("You can only assign issues to a pod you are assigned to");
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
		String assigneeEmail = i.getAssignee() != null ? i.getAssignee().getEmail() : i.getJiraAssigneeEmail();
		String assigneeDisplayName = i.getAssignee() != null
				? i.getAssignee().getDisplayName()
				: (i.getJiraAssigneeDisplayName() != null && !i.getJiraAssigneeDisplayName().isBlank()
						? i.getJiraAssigneeDisplayName()
						: null);
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
				assigneeEmail,
				assigneeDisplayName,
				i.getPortalReporter() != null ? i.getPortalReporter().getId() : null,
				i.getPortalReporter() != null ? i.getPortalReporter().getEmail() : null,
				i.getJiraReporterEmail(),
				i.getJiraReporterDisplayName(),
				i.getCreatedBy().getId(),
				i.getCreatedBy().getEmail(),
				i.getLastSyncedAt());
	}

	public String getTicketStatus(String jiraKey) {

		Issue issue = issueRepository.findByJiraIssueKey(jiraKey)
				.orElse(null);

		if (issue == null) {
			return "Ticket " + jiraKey + " not found in system.";
		}

		return "Ticket: " + jiraKey +
				"\nStatus: " + issue.getJiraStatus() +
				"\nSummary: " + issue.getTitle() +
				"\nAssignee: " + (issue.getAssignee() != null ? issue.getAssignee().getEmail() : "Unassigned");
	}
	public String getCompletedThisWeek() {

		List<Issue> issues = issueRepository.findAll();

		java.time.LocalDate now = java.time.LocalDate.now();
		java.time.LocalDate startOfWeek = now.minusDays(now.getDayOfWeek().getValue() - 1);

		List<Issue> completed = issues.stream()
				.filter(i -> "Done".equalsIgnoreCase(i.getJiraStatus()))
				.filter(i -> i.getClosingDate() != null &&
						!i.getClosingDate().isBefore(startOfWeek))
				.toList();

		if (completed.isEmpty()) {
			return "No tickets completed this week.";
		}

		StringBuilder sb = new StringBuilder("Completed this week:\n");
		for (Issue i : completed) {
			sb.append(i.getJiraIssueKey())
					.append(" - ")
					.append(i.getTitle())
					.append("\n");
		}

		return sb.toString();
	}
	public String getBlockedTickets() {

		List<Issue> issues = issueRepository.findAll();

		List<Issue> blocked = issues.stream()
				.filter(i -> "Blocked".equalsIgnoreCase(i.getJiraStatus()))
				.toList();

		if (blocked.isEmpty()) {
			return "No blocked tickets.";
		}

		StringBuilder sb = new StringBuilder("Blocked tickets:\n");
		for (Issue i : blocked) {
			sb.append(i.getJiraIssueKey())
					.append(" - ")
					.append(i.getTitle())
					.append("\n");
		}

		return sb.toString();
	}

	public String getHighPriorityIssues() {

		List<Issue> issues = issueRepository.findAll();

		List<Issue> high = issues.stream()
				.filter(i -> i.getSeverity() != null && i.getSeverity() == 1)
				.toList();

		if (high.isEmpty()) {
			return "No high priority issues.";
		}

		StringBuilder sb = new StringBuilder("High priority issues:\n");
		for (Issue i : high) {
			sb.append(i.getJiraIssueKey())
					.append(" - ")
					.append(i.getTitle())
					.append("\n");
		}

		return sb.toString();
	}
}
