package com.scai.customer_portal.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Jira Cloud often returns timestamps like {@code 2024-04-04T10:15:30.000+0530} (offset without colon).
 */
public final class JiraDateParsing {

	private JiraDateParsing() {
	}

	public static LocalDate parseLocalDate(String raw) {
		if (raw == null) {
			return null;
		}
		String s = raw.trim();
		if (s.isEmpty()) {
			return null;
		}
		if (s.length() >= 10 && s.charAt(4) == '-' && s.charAt(7) == '-') {
			try {
				return LocalDate.parse(s.substring(0, 10));
			}
			catch (DateTimeParseException ignored) {
				// continue with full timestamp parsing
			}
		}
		try {
			return Instant.parse(s).atZone(ZoneOffset.UTC).toLocalDate();
		}
		catch (DateTimeParseException ignored) {
		}
		try {
			return OffsetDateTime.parse(s).toLocalDate();
		}
		catch (DateTimeParseException ignored) {
		}
		try {
			return ZonedDateTime.parse(s, DateTimeFormatter.ISO_ZONED_DATE_TIME).toLocalDate();
		}
		catch (DateTimeParseException ignored) {
		}
		try {
			DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
			return OffsetDateTime.parse(s, f).toLocalDate();
		}
		catch (DateTimeParseException ignored) {
		}
		try {
			DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
			return ZonedDateTime.parse(s, f).toLocalDate();
		}
		catch (DateTimeParseException ignored) {
		}
		try {
			DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
			return ZonedDateTime.parse(s, f).toLocalDate();
		}
		catch (DateTimeParseException ignored) {
		}
		try {
			DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxx");
			return OffsetDateTime.parse(s, f).toLocalDate();
		}
		catch (DateTimeParseException ignored) {
		}
		return null;
	}

	/** Jira {@code fields} timestamp fields are normally JSON strings. */
	public static String timestampText(JsonNode fields, String fieldKey) {
		if (fields == null || fields.isMissingNode()) {
			return null;
		}
		JsonNode n = fields.get(fieldKey);
		if (n == null || n.isNull()) {
			return null;
		}
		if (n.isTextual()) {
			return n.asText();
		}
		return null;
	}
}
