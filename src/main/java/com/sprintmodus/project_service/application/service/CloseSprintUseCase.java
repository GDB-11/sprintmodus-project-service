package com.sprintmodus.project_service.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sprintmodus.common_lib.result.Result;
import com.sprintmodus.common_lib.result.Unit;
import com.sprintmodus.project_service.application.error.SprintError;
import com.sprintmodus.project_service.application.error.SprintError.InvalidSprintState;
import com.sprintmodus.project_service.application.error.SprintError.SprintNotFound;
import com.sprintmodus.project_service.application.port.persistence.SprintRepository;
import com.sprintmodus.project_service.domain.model.SprintStatus;

/** Closes an active sprint. Its velocity stays as last reported and is frozen from then on. */
@Service
public class CloseSprintUseCase {

	private final SprintRepository sprints;

	public CloseSprintUseCase(SprintRepository sprints) {
		this.sprints = sprints;
	}

	public Result<Unit, SprintError> execute(UUID sprintCode) {
		var sprint = sprints.findByCode(sprintCode).orElse(null);
		if (sprint == null) {
			return Result.failure(new SprintNotFound());
		}
		if (sprint.status() != SprintStatus.ACTIVE) {
			return Result.failure(new InvalidSprintState("Only an active sprint can be closed."));
		}
		// Another request may have closed it since the read above
		return sprints.close(sprintCode) ? Result.success(Unit.VALUE)
				: Result.failure(new InvalidSprintState("Only an active sprint can be closed."));
	}

}
