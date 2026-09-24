package com.sprintmodus.project_service.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Persistence model of a row of the {@code view_Sprint_Overview} read model (a database view, so it is only read). */
@Entity
@Table(name = "view_Sprint_Overview")
class SprintOverviewEntity {

	@Id
	@Column(name = "SprintId")
	Long id;

	@Column(name = "TotalItems")
	int totalItems;

	@Column(name = "CompletedItems")
	int completedItems;

	@Column(name = "PlannedEffort")
	int plannedEffort;

	@Column(name = "CompletedEffort")
	int completedEffort;

	@Column(name = "DefectCount")
	int defectCount;

}
