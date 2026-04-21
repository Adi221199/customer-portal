package com.scai.customer_portal.api.dto.dashboard;

import org.springframework.lang.Nullable;

import java.util.UUID;

/**
 * Jira assignee for filtering — portal {@code assignee} when linked, otherwise Jira assignee email/display when the issue has no
 * portal user match.
 */
public record AssigneeOption(@Nullable UUID id, String email, String displayName) {
}
