package com.sprintmodus.project_service.application.service;

import com.sprintmodus.project_service.application.error.SprintError;
import com.sprintmodus.project_service.application.error.SprintError.AnotherSprintActive;
import com.sprintmodus.project_service.application.error.SprintError.InvalidSprintState;
import com.sprintmodus.project_service.application.error.SprintError.ProjectNotFound;
import com.sprintmodus.project_service.application.error.SprintError.SprintNotFound;
import com.sprintmodus.project_service.application.error.SprintError.SprintOverlap;
import com.sprintmodus.project_service.application.port.persistence.SprintRepository;

/** Turns what the sprint repository rejected an operation for into the error the caller sees. */
final class SprintErrors {

	private SprintErrors() {
	}

	static SprintError from(SprintRepository.Rejection rejection) {
		return switch (rejection) {
			case PROJECT_NOT_FOUND -> new ProjectNotFound();
			case SPRINT_NOT_FOUND -> new SprintNotFound();
			case OVERLAP -> new SprintOverlap();
			case NOT_PLANNED -> new InvalidSprintState("Only a planned sprint can be started.");
			case CLOSED -> new InvalidSprintState("A closed sprint cannot be changed.");
			case ANOTHER_ACTIVE -> new AnotherSprintActive();
		};
	}

}
