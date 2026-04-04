package com.scai.customer_portal.api;

import com.scai.customer_portal.api.dto.ImportJiraRequest;
import com.scai.customer_portal.api.dto.IssuePatchRequest;
import com.scai.customer_portal.api.dto.IssueResponse;
import com.scai.customer_portal.service.IssueService;
import jakarta.validation.Valid;
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
