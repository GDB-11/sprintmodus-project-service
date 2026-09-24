package com.sprintmodus.project_service.infrastructure.persistence;

import java.time.LocalDate;
import java.util.UUID;

import com.sprintmodus.project_service.domain.model.SprintStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Persistence model of a sprint with its project's code, as returned by the native queries in {@link SprintQueries}. */
@Entity
@Table(name = "Sprint")
class SprintEntity {

	@Id
	@Column(name = "SprintId")
	Long id;

	@Column(name = "SprintCode")
	UUID code;

	@Column(name = "ProjectCode")
	UUID projectCode;

	@Column(name = "Name")
	String name;

	@Enumerated(EnumType.STRING)
	@Column(name = "Status")
	SprintStatus status;

	@Column(name = "ConfiguredDays")
	int configuredDays;

	@Column(name = "StartDate")
	LocalDate startDate;

	@Column(name = "EndDate")
	LocalDate endDate;

	@Column(name = "PlannedVelocity")
	int plannedVelocity;

	@Column(name = "Velocity")
	int velocity;

}
