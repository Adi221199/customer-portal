package com.scai.customer_portal.api;

import com.scai.customer_portal.api.dto.BulkImportOptionsResponse;
import com.scai.customer_portal.api.dto.BulkJiraImportJobAcceptedResponse;
import com.scai.customer_portal.api.dto.BulkJiraImportJobStatusResponse;
import com.scai.customer_portal.api.dto.BulkJiraImportRequest;
import com.scai.customer_portal.api.dto.ImportJiraRequest;
import com.scai.customer_portal.api.dto.IssuePatchRequest;
import com.scai.customer_portal.api.dto.IssueResponse;
import com.scai.customer_portal.service.IssueService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/issues")
public class IssueController {

	private final IssueService issueService;

	public IssueController(IssueService issueService) {
		this.issueService = issueService;
	}

	@GetMapping
	public List<IssueResponse> list() {
		return issueService.listForCurrentUser();
	}

	@PostMapping("/import-jira")
	public IssueResponse importJira(@Valid @RequestBody ImportJiraRequest request) {
		return issueService.importFromJira(request);
	}

	/** Dropdown data for bulk JQL filters ({@code SC_ADMIN} only). */
	@GetMapping("/bulk-import-options")
	public BulkImportOptionsResponse bulkImportOptions() {
		return issueService.bulkImportOptions();
	}

	/**
	 * Queues JQL bulk import in a background thread. Returns {@code 202 Accepted} with a {@code jobId}; poll
	 * {@code GET /api/issues/import-jira-bulk/jobs/{jobId}} until {@code COMPLETED} or {@code FAILED}.
	 */
	@PostMapping("/import-jira-bulk")
	public ResponseEntity<BulkJiraImportJobAcceptedResponse> importJiraBulk(
			@RequestBody(required = false) @Valid BulkJiraImportRequest body) {
		BulkJiraImportJobAcceptedResponse accepted = issueService.startBulkImportJob(
				body == null ? BulkJiraImportRequest.defaults() : body);
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(accepted);
	}

	@GetMapping("/import-jira-bulk/jobs/{jobId}")
	public BulkJiraImportJobStatusResponse bulkImportJobStatus(@PathVariable UUID jobId) {
		return issueService.getBulkImportJobStatus(jobId);
	}

	/** Re-fetch from Jira for this portal row (same as re-import by key, but uses stored {@code jiraIssueKey}). */
	@PostMapping("/{id}/sync-from-jira")
	public IssueResponse syncFromJira(@PathVariable UUID id) {
		return issueService.syncIssueFromJira(id);
	}

	@PatchMapping("/{id}")
	public IssueResponse patch(@PathVariable UUID id, @Valid @RequestBody IssuePatchRequest request) {
		return issueService.patchIssue(id, request);
	}
}
