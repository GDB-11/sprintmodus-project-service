package com.sprintmodus.project_service.adapter.rest.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.sprintmodus.common_lib.result.ApplicationError;
import com.sprintmodus.common_lib.web.ErrorResponse;
import com.sprintmodus.project_service.application.error.ProjectError;
import com.sprintmodus.project_service.application.error.SprintConfigError;
import com.sprintmodus.project_service.application.error.SprintError;

/**
 * The HTTP status of every business error. The switches are exhaustive over the sealed hierarchies, so adding an error
 * type without deciding its status does not compile. Messages never carry tenant or user details.
 */
final class ErrorMapper {

	private ErrorMapper() {
	}

	@SuppressWarnings("unchecked")
	static <T> ResponseEntity<T> toResponse(ApplicationError error) {
		return (ResponseEntity<T>) ResponseEntity.status(status(error)).body(new ErrorResponse(error.code(), error.message()));
	}

	static HttpStatus status(ApplicationError error) {
		return switch (error) {
			case ProjectError project -> status(project);
			case SprintError sprint -> status(sprint);
			case SprintConfigError config -> status(config);
			default -> HttpStatus.INTERNAL_SERVER_ERROR;
		};
	}

	private static HttpStatus status(ProjectError error) {
		return switch (error) {
			case ProjectError.InvalidProjectData _ -> HttpStatus.BAD_REQUEST;
			case ProjectError.ProjectNotFound _ -> HttpStatus.NOT_FOUND;
			case ProjectError.ProjectKeyTaken _ -> HttpStatus.CONFLICT;
			case ProjectError.ProjectLimitReached _ -> HttpStatus.PAYMENT_REQUIRED;
			case ProjectError.NotAllowed _ -> HttpStatus.FORBIDDEN;
		};
	}

	private static HttpStatus status(SprintError error) {
		return switch (error) {
			case SprintError.InvalidSprintData _ -> HttpStatus.BAD_REQUEST;
			case SprintError.ProjectNotFound _, SprintError.SprintNotFound _ -> HttpStatus.NOT_FOUND;
			case SprintError.SprintOverlap _, SprintError.InvalidSprintState _, SprintError.AnotherSprintActive _ -> HttpStatus.CONFLICT;
			case SprintError.NotAllowed _ -> HttpStatus.FORBIDDEN;
		};
	}

	private static HttpStatus status(SprintConfigError error) {
		return switch (error) {
			case SprintConfigError.InvalidSprintConfig _ -> HttpStatus.BAD_REQUEST;
			case SprintConfigError.NotAllowed _ -> HttpStatus.FORBIDDEN;
		};
	}

}
