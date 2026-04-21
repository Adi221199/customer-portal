package com.scai.customer_portal.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "app_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(name = "display_name", nullable = false)
	private String displayName;

	@Column(nullable = false)
	private boolean enabled = true;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "organization_id")
	private Organization organization;

	/**
	 * SC_LEAD: Jira project / module codes (issue key prefix, e.g. EDM, DPAI) this user may see. Assigned by admin; no separate pod entity.
	 */
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "app_user_modules", joinColumns = @JoinColumn(name = "user_id"))
	@Column(name = "module_code", nullable = false, length = 200)
	@Builder.Default
	private Set<String> assignedModules = new HashSet<>();

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "app_user_roles", joinColumns = @JoinColumn(name = "user_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "role")
	@Builder.Default
	private Set<PortalRole> roles = new HashSet<>();

	/** Whether this SC_LEAD user is assigned the given module / project key (case-insensitive). */
	public boolean isAssignedToModule(String moduleCode) {
		if (moduleCode == null || moduleCode.isBlank() || assignedModules == null || assignedModules.isEmpty()) {
			return false;
		}
		String m = moduleCode.trim().toLowerCase(Locale.ROOT);
		return assignedModules.stream()
				.filter(s -> s != null && !s.isBlank())
				.anyMatch(s -> s.trim().toLowerCase(Locale.ROOT).equals(m));
	}
}
