package com.scai.customer_portal.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * Public metadata for an attachment stored in {@code issues.attachments_json} (file bytes on server disk).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IssueAttachmentInfo(
		UUID id,
		String fileName,
		String contentType,
		long sizeBytes) {
}
