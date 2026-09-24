package com.sprintmodus.project_service.domain.model;

import java.util.UUID;

/** A user assigned to a project. */
public record ProjectMember(UUID userCode, String fullName, String email, OrganizationRole role) {
}
