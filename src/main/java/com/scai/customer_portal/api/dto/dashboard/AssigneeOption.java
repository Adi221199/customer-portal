package com.scai.customer_portal.api.dto.dashboard;

import java.util.UUID;

/** Delivery SPOC — portal assignee on the issue. */
public record AssigneeOption(UUID id, String email, String displayName) {
}
