package com.scai.customer_portal.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePodRequest(
		@NotBlank @Size(min = 1, max = 200) String name
) {
}
