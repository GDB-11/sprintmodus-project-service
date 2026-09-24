package com.sprintmodus.project_service.application.error;

import com.sprintmodus.common_lib.result.ApplicationError;

/** Why a project operation was refused. */
public sealed interface ProjectError extends ApplicationError {

	/** A field is missing or malformed; {@code message} says what to fix. */
	record InvalidProjectData(String field, String message) implements ProjectError {

		@Override
		public String code() {
			return "INVALID_PROJECT_DATA";
		}

	}

	record ProjectNotFound() implements ProjectError {

		@Override
		public String code() {
			return "PROJECT_NOT_FOUND";
		}

		@Override
		public String message() {
			return "Project not found.";
		}

	}

	record ProjectKeyTaken() implements ProjectError {

		@Override
		public String code() {
			return "PROJECT_KEY_TAKEN";
		}

		@Override
		public String message() {
			return "This project key is already in use.";
		}

	}

	/** The plan's project limit (from the JWT) is reached. */
	record ProjectLimitReached(int maxProjects) implements ProjectError {

		@Override
		public String code() {
			return "PROJECT_LIMIT_REACHED";
		}

		@Override
		public String message() {
			return "Your plan allows at most " + maxProjects + (maxProjects == 1 ? " project" : " projects")
					+ ". Upgrade your plan to create more.";
		}

	}

	record NotAllowed() implements ProjectError {

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
