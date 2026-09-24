package com.sprintmodus.project_service.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sprintmodus.common_lib.result.Result;
import com.sprintmodus.common_lib.result.Unit;
import com.sprintmodus.project_service.application.error.SprintError;
import com.sprintmodus.project_service.application.error.SprintError.InvalidSprintData;
import com.sprintmodus.project_service.application.error.SprintError.InvalidSprintState;
import com.sprintmodus.project_service.application.error.SprintError.SprintNotFound;
import com.sprintmodus.project_service.application.port.persistence.SprintConfigRepository;
import com.sprintmodus.project_service.application.port.persistence.SprintRepository;
import com.sprintmodus.project_service.domain.model.SprintStatus;

/**
 * Records the effort points completed in a sprint. Called by workitem-service (which owns the items and computes the
 * number) with the caller's own token. When the tenant switched velocity tracking off the call succeeds and records
 * nothing, so workitem-service does not need to know the setting. A closed sprint's velocity is frozen.
 */
@Service
public class UpdateSprintVelocityUseCase {

	private static final int MAX_VELOCITY = 1_000_000;

	private final SprintRepository sprints;

	private final SprintConfigRepository configs;

	public UpdateSprintVelocityUseCase(SprintRepository sprints, SprintConfigRepository configs) {
		this.sprints = sprints;
		this.configs = configs;
	}

	public Result<Unit, SprintError> execute(UUID sprintCode, int velocity) {
		if (velocity < 0 || velocity > MAX_VELOCITY) {
			return Result.failure(new InvalidSprintData("velocity", "The velocity must be zero or more."));
		}
		var sprint = sprints.findByCode(sprintCode).orElse(null);
		if (sprint == null) {
			return Result.failure(new SprintNotFound());
		}
		if (!configs.find().velocityTrackingEnabled()) {
			return Result.success(Unit.VALUE);
		}
		if (sprint.status() == SprintStatus.CLOSED) {
			return Result.failure(new InvalidSprintState("The velocity of a closed sprint cannot be changed."));
		}
		return sprints.updateVelocity(sprintCode, velocity) ? Result.success(Unit.VALUE)
				: Result.failure(new InvalidSprintState("The velocity of a closed sprint cannot be changed."));
	}

}
