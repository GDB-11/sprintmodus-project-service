package com.sprintmodus.project_service.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sprintmodus.common_lib.result.Result;
import com.sprintmodus.project_service.application.dto.Actor;
import com.sprintmodus.project_service.application.dto.Responses.SprintResponse;
import com.sprintmodus.project_service.application.error.SprintError;
import com.sprintmodus.project_service.application.error.SprintError.NotAllowed;
import com.sprintmodus.project_service.application.port.persistence.SprintRepository;

/**
 * Starts a planned sprint (owners and admins only). A project has at most one active sprint at a time. Starting it also
 * records the burndown's day 0: the hours in the sprint before its first day.
 */
@Service
public class StartSprintUseCase {

	private final SprintRepository sprints;

	public StartSprintUseCase(SprintRepository sprints) {
		this.sprints = sprints;
	}

	public Result<SprintResponse, SprintError> execute(Actor actor, UUID sprintCode) {
		if (!actor.canAdminister()) {
			return Result.failure(new NotAllowed());
		}
		return sprints.start(sprintCode).mapError(SprintErrors::from).map(SprintResponse::from);
	}

}
