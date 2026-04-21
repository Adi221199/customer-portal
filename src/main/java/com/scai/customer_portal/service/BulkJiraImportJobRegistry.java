package com.scai.customer_portal.service;

import com.scai.customer_portal.api.dto.BulkJiraImportJobStatusResponse;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class BulkJiraImportJobRegistry {

	private static final int MAX_JOBS = 400;

	private final ConcurrentHashMap<UUID, Job> jobs = new ConcurrentHashMap<>();

	public UUID createJob(UUID ownerUserId) {
		evictIfNeeded();
		UUID id = UUID.randomUUID();
		jobs.put(id, new Job(id, ownerUserId, Instant.now()));
		return id;
	}

	public Optional<Job> get(UUID jobId) {
		return Optional.ofNullable(jobs.get(jobId));
	}

	public void markRunning(UUID jobId) {
		Job j = jobs.get(jobId);
		if (j != null) {
			j.markRunning();
		}
	}

	public void setSearchResult(UUID jobId, int totalKeys) {
		Job j = jobs.get(jobId);
		if (j != null) {
			j.setTotalKeys(totalKeys);
		}
	}

	public void recordSuccess(UUID jobId) {
		Job j = jobs.get(jobId);
		if (j != null) {
			j.recordSuccess();
		}
	}

	public void recordFailure(UUID jobId, String key, String message) {
		Job j = jobs.get(jobId);
		if (j != null) {
			j.recordFailure(key, message);
		}
	}

	public void markCompleted(UUID jobId) {
		Job j = jobs.get(jobId);
		if (j != null) {
			j.markCompleted();
		}
	}

	public void markFailed(UUID jobId, String fatalError) {
		Job j = jobs.get(jobId);
		if (j != null) {
			j.markFailed(fatalError);
		}
	}

	public BulkJiraImportJobStatusResponse toResponse(Job j) {
		return j.toResponse();
	}

	private void evictIfNeeded() {
		if (jobs.size() < MAX_JOBS) {
			return;
		}
		List<Map.Entry<UUID, Job>> list = new ArrayList<>(jobs.entrySet());
		list.sort(java.util.Comparator.comparing(e -> e.getValue().createdAt));
		for (Map.Entry<UUID, Job> e : list) {
			if (jobs.size() < MAX_JOBS * 3 / 4) {
				break;
			}
			Job j = e.getValue();
			if (j.status == BulkJiraImportJobStatus.COMPLETED || j.status == BulkJiraImportJobStatus.FAILED) {
				jobs.remove(e.getKey());
			}
		}
	}

	public static final class Job {
		public final UUID jobId;
		public final UUID ownerUserId;
		public final Instant createdAt;
		private BulkJiraImportJobStatus status = BulkJiraImportJobStatus.QUEUED;
		private int totalKeysFromJira = -1;
		private int importedOrUpdated;
		private int failed;
		private final List<String> failureSamples = new ArrayList<>();
		private String fatalError;
		private Instant startedAt;
		private Instant completedAt;

		Job(UUID jobId, UUID ownerUserId, Instant createdAt) {
			this.jobId = jobId;
			this.ownerUserId = ownerUserId;
			this.createdAt = createdAt;
		}

		private synchronized void markRunning() {
			status = BulkJiraImportJobStatus.RUNNING;
			startedAt = Instant.now();
		}

		private synchronized void setTotalKeys(int total) {
			totalKeysFromJira = total;
		}

		private synchronized void recordSuccess() {
			importedOrUpdated++;
		}

		private synchronized void recordFailure(String key, String message) {
			failed++;
			if (failureSamples.size() < 20) {
				String m = message == null ? "" : message;
				if (m.length() > 400) {
					m = m.substring(0, 400) + "…";
				}
				failureSamples.add(key + ": " + m);
			}
		}

		private synchronized void markCompleted() {
			status = BulkJiraImportJobStatus.COMPLETED;
			completedAt = Instant.now();
		}

		private synchronized void markFailed(String error) {
			status = BulkJiraImportJobStatus.FAILED;
			fatalError = error;
			completedAt = Instant.now();
		}

		synchronized BulkJiraImportJobStatusResponse toResponse() {
			int processed = importedOrUpdated + failed;
			return new BulkJiraImportJobStatusResponse(
					jobId,
					status.name(),
					totalKeysFromJira,
					importedOrUpdated,
					failed,
					processed,
					List.copyOf(failureSamples),
					fatalError,
					createdAt,
					startedAt,
					completedAt);
		}
	}
}
