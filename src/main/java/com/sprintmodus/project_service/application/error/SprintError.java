package com.sprintmodus.project_service.application.error;

import com.sprintmodus.common_lib.result.ApplicationError;

/** Why a sprint operation was refused. */
public sealed interface SprintError extends ApplicationError {

	record InvalidSprintData(String field, String message) implements SprintError {

		@Override
		public String code() {
			return "INVALID_SPRINT_DATA";
		}

	}

	record ProjectNotFound() implements SprintError {

		@Override
		public String code() {
			return "PROJECT_NOT_FOUND";
		}

		@Override
		public String message() {
			return "Project not found.";
		}

	}

	record SprintNotFound() implements SprintError {

		@Override
		public String code() {
			return "SPRINT_NOT_FOUND";
		}

		@Override
		public String message() {
			return "Sprint not found.";
		}

	}

	/** The sprint's dates share a day with another sprint of the same project. */
	record SprintOverlap() implements SprintError {

		@Override
		public String code() {
			return "SPRINT_OVERLAP";
		}

		@Override
		public String message() {
			return "These dates overlap another sprint of the project.";
		}

	}

	/** The operation does not apply to the sprint's current status. */
	record InvalidSprintState(String message) implements SprintError {

		@Override
		public String code() {
			return "INVALID_SPRINT_STATE";
		}

	}

	record AnotherSprintActive() implements SprintError {

		@Override
		public String code() {
			return "ANOTHER_SPRINT_ACTIVE";
		}

		@Override
		public String message() {
			return "Another sprint of this project is already active.";
		}

	}

}
