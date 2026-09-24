package com.sprintmodus.project_service.application.error;

import com.sprintmodus.common_lib.result.ApplicationError;

/** Why the sprint configuration could not be changed. */
public sealed interface SprintConfigError extends ApplicationError {

	record InvalidSprintConfig(String field, String message) implements SprintConfigError {

		@Override
		public String code() {
			return "INVALID_SPRINT_CONFIG";
		}

	}

	record NotAllowed() implements SprintConfigError {

		@Override
		public String code() {
			return "FORBIDDEN";
		}

		@Override
		public String message() {
			return "You do not have permission to do this.";
		}

	}

}
