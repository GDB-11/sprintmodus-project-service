package com.sprintmodus.project_service.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sprintmodus.common_lib.result.Result;
import com.sprintmodus.project_service.application.dto.Responses.SprintResponse;
import com.sprintmodus.project_service.application.error.SprintError;
import com.sprintmodus.project_service.application.port.persistence.SprintRepository;

/** Starts a planned sprint. A project has at most one active sprint at a time. */
@Service
public class StartSprintUseCase {

	private final SprintRepository sprints;

	public StartSprintUseCase(SprintRepository sprints) {
		this.sprints = sprints;
	}

	public Result<SprintResponse, SprintError> execute(UUID sprintCode) {
		return sprints.start(sprintCode).mapError(SprintErrors::from).map(SprintResponse::from);
	}

}
