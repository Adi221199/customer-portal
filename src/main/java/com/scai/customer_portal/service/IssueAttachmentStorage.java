package com.scai.customer_portal.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scai.customer_portal.api.dto.IssueAttachmentInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Persists upload parts under {@code customer-portal.issue-upload-dir/issues/{issueId}/}.
 */
@Component
public class IssueAttachmentStorage {

	public static final int MAX_FILES = 8;
	public static final long MAX_BYTES_PER_FILE = 8 * 1024 * 1024; // 8 MB

	private final Path root;
	private final ObjectMapper objectMapper;

	public IssueAttachmentStorage(
			@Value("${customer-portal.issue-upload-dir:./data/issue-uploads}") String baseDir,
			ObjectMapper objectMapper) {
		this.root = Path.of(baseDir).toAbsolutePath().normalize();
		this.objectMapper = objectMapper;
	}

	public Path getRoot() {
		return root;
	}

	/**
	 * Writes files and returns JSON to store on {@code Issue#attachmentsJson}. No DB write here.
	 */
	public String saveAndSerialize(UUID issueId, List<MultipartFile> files) throws IOException {
		if (files == null || files.isEmpty()) {
			return "[]";
		}
		if (files.size() > MAX_FILES) {
			throw new IllegalArgumentException("At most " + MAX_FILES + " files allowed");
		}
		List<PersistedRow> rows = new ArrayList<>();
		Path dir = root.resolve("issues").resolve(issueId.toString());
		Files.createDirectories(dir);
		for (MultipartFile f : files) {
			if (f == null || f.isEmpty()) {
				continue;
			}
			if (f.getSize() > MAX_BYTES_PER_FILE) {
				throw new IllegalArgumentException("File too large: " + f.getOriginalFilename());
			}
			UUID fileId = UUID.randomUUID();
			String orig = f.getOriginalFilename() != null ? f.getOriginalFilename() : "file";
			String safe = orig.replaceAll("[^a-zA-Z0-9._-]+", "_");
			if (safe.isBlank() || safe.length() > 180) {
				safe = "file";
			}
			String stored = fileId + "_" + safe;
			Path target = dir.resolve(stored);
			Files.copy(f.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
			String ct = f.getContentType() != null ? f.getContentType() : "application/octet-stream";
			rows.add(new PersistedRow(
					fileId, orig, ct, f.getSize(), stored, issueId));
		}
		return objectMapper.writeValueAsString(rows);
	}

	public List<IssueAttachmentInfo> toPublicList(String attachmentsJson) {
		if (attachmentsJson == null || attachmentsJson.isBlank() || "[]".equals(attachmentsJson.trim())) {
			return List.of();
		}
		try {
			List<PersistedRow> list = objectMapper.readValue(attachmentsJson, new TypeReference<>() {
			});
			return list.stream()
					.map(r -> new IssueAttachmentInfo(r.id, r.fileName, r.contentType, r.sizeBytes))
					.collect(Collectors.toList());
		}
		catch (Exception e) {
			return List.of();
		}
	}

	/** For authorized download. */
	public Path resolveStoredFile(UUID issueId, UUID fileId, String attachmentsJson) {
		if (attachmentsJson == null) {
			return null;
		}
		try {
			List<PersistedRow> list = objectMapper.readValue(attachmentsJson, new TypeReference<>() {
			});
			for (PersistedRow r : list) {
				if (r.id().equals(fileId) && r.issueId().equals(issueId)) {
					return root.resolve("issues").resolve(issueId.toString()).resolve(r.storedName());
				}
			}
		}
		catch (Exception ignored) {
		}
		return null;
	}

	public String contentTypeForDownload(UUID issueId, UUID fileId, String attachmentsJson) {
		if (attachmentsJson == null) {
			return "application/octet-stream";
		}
		try {
			List<PersistedRow> list = objectMapper.readValue(attachmentsJson, new TypeReference<>() {
			});
			for (PersistedRow r : list) {
				if (r.id().equals(fileId) && r.issueId().equals(issueId)) {
					return r.contentType() != null
							? r.contentType()
							: "application/octet-stream";
				}
			}
		}
		catch (Exception ignored) {
		}
		return "application/octet-stream";
	}

	/** JSON row shape. */
	public record PersistedRow(
			UUID id,
			String fileName,
			String contentType,
			long sizeBytes,
			String storedName,
			UUID issueId) {
	}
}
