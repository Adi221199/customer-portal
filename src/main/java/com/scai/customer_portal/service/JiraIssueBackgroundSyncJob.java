package com.scai.customer_portal.service;

import com.scai.customer_portal.repository.IssueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Periodically re-fetches Jira-linked issues and applies <strong>progress-only</strong> updates (status, summary,
 * resolution date, priority). Customer/org, env, module, RCA, assignee, description, etc. are left unchanged
 * so portal-filled fields are not wiped when Jira omits them. Disabled unless {@code jira.background-sync-enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "jira.background-sync-enabled", havingValue = "true")
public class JiraIssueBackgroundSyncJob {

	private static final Logger log = LoggerFactory.getLogger(JiraIssueBackgroundSyncJob.class);

	private final IssueRepository issueRepository;
	private final IssueService issueService;

	public JiraIssueBackgroundSyncJob(IssueRepository issueRepository, IssueService issueService) {
		this.issueRepository = issueRepository;
		this.issueService = issueService;
	}

	@Scheduled(cron = "${jira.background-sync-cron:0 0 */4 * * *}")
	public void syncAllLinkedIssues() {
		if (!issueService.isJiraIntegrationConfigured()) {
			return;
		}
		List<UUID> ids = issueRepository.findAllIdsWithJiraIssueKey();
		log.info("Jira background sync: {} issue(s)", ids.size());
		for (UUID id : ids) {
			try {
				issueService.syncImportedIssueFromJiraForBackground(id);
			}
			catch (Exception e) {
				log.warn("Jira background sync failed for issue id {}: {}", id, e.getMessage());
			}
		}
	}
}
