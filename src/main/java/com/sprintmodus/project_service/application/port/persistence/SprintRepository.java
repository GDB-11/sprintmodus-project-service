package com.sprintmodus.project_service.application.port.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.sprintmodus.common_lib.result.Result;
import com.sprintmodus.project_service.domain.model.Sprint;
import com.sprintmodus.project_service.domain.model.SprintMetrics;

/** Sprints of the current tenant. Every change is checked and applied atomically, per project. */
public interface SprintRepository {

	enum Rejection {

		PROJECT_NOT_FOUND,
		SPRINT_NOT_FOUND,
		OVERLAP,
		NOT_PLANNED,
		CLOSED,
		ANOTHER_ACTIVE

	}

	record NewSprint(UUID projectCode, String name, int configuredDays, LocalDate startDate, LocalDate endDate,
			int plannedVelocity, UUID createdBy) {
	}

	record SprintChanges(UUID sprintCode, String name, LocalDate startDate, LocalDate endDate, int plannedVelocity) {
	}

	/** Rejects with {@code PROJECT_NOT_FOUND} or {@code OVERLAP}. */
	Result<Sprint, Rejection> create(NewSprint sprint);

	Optional<Sprint> findByCode(UUID code);

	/** The active sprints of an active project, by start date. */
	List<Sprint> findByProject(UUID projectCode);

	/** Rejects with {@code SPRINT_NOT_FOUND}, {@code CLOSED} or {@code OVERLAP}. */
	Result<Sprint, Rejection> update(SprintChanges changes);

	/** Moves PLANNED to ACTIVE. Rejects with {@code SPRINT_NOT_FOUND}, {@code NOT_PLANNED} or {@code ANOTHER_ACTIVE}. */
	Result<Sprint, Rejection> start(UUID sprintCode);

	/** Moves ACTIVE to CLOSED. Returns whether the sprint was active. */
	boolean close(UUID sprintCode);

	/** Records the velocity of a sprint that is not closed. Returns whether it was recorded. */
	boolean updateVelocity(UUID sprintCode, int velocity);

	/** What the sprint's work items add up to, or empty for an unknown sprint. */
	Optional<SprintMetrics> findMetrics(UUID sprintCode);

}
