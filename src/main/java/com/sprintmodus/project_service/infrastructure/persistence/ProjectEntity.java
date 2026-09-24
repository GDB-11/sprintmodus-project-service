package com.sprintmodus.project_service.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Persistence model of a project, as returned by the native queries in {@link ProjectQueries} (which alias
 * {@code Key} to {@code ProjectKey} and add the creator's user code). It is never used to generate SQL.
 */
@Entity
@Table(name = "Project")
class ProjectEntity {

	@Id
	@Column(name = "ProjectId")
	Long id;

	@Column(name = "ProjectCode")
	UUID code;

	@Column(name = "Name")
	String name;

	@Column(name = "Description")
	String description;

	@Column(name = "ProjectKey")
	String key;

	@Column(name = "CreatedByCode")
	UUID createdByCode;

	@Column(name = "CreatedAt")
	Instant createdAt;

}
