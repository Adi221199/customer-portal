package com.scai.customer_portal.api;

import com.scai.customer_portal.api.dto.ImportJiraRequest;
import com.scai.customer_portal.api.dto.IssueResponse;
import com.scai.customer_portal.service.IssueService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
}
