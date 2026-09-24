package com.sprintmodus.project_service.infrastructure.persistence;

import java.util.UUID;

import com.sprintmodus.project_service.domain.model.OrganizationRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Persistence model of a project member joined with their tenant user, as returned by {@link ProjectMemberQueries}. */
@Entity
@Table(name = "ProjectMember")
class ProjectMemberEntity {

	@Id
	@Column(name = "ProjectMemberId")
	Long id;

	@Column(name = "UserCode")
	UUID userCode;

	@Column(name = "FullName")
	String fullName;

	@Column(name = "Email")
	String email;

	@Enumerated(EnumType.STRING)
	@Column(name = "Role")
	OrganizationRole role;

}
