package com.sprintmodus.project_service.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sprintmodus.common_lib.result.Result;
import com.sprintmodus.project_service.application.dto.Responses.VelocityResponse;
import com.sprintmodus.project_service.application.error.SprintError;
import com.sprintmodus.project_service.application.error.SprintError.SprintNotFound;
import com.sprintmodus.project_service.application.port.persistence.SprintConfigRepository;
import com.sprintmodus.project_service.application.port.persistence.SprintRepository;
import com.sprintmodus.project_service.domain.model.SprintMetrics;

/** The velocity recorded for a sprint next to what its work items currently add up to. */
@Service
public class GetSprintVelocityUseCase {

	private final SprintRepository sprints;

	private final SprintConfigRepository configs;

	public GetSprintVelocityUseCase(SprintRepository sprints, SprintConfigRepository configs) {
		this.sprints = sprints;
		this.configs = configs;
	}

	public Result<VelocityResponse, SprintError> execute(UUID sprintCode) {
		var sprint = sprints.findByCode(sprintCode).orElse(null);
		if (sprint == null) {
			return Result.failure(new SprintNotFound());
		}
		SprintMetrics metrics = sprints.findMetrics(sprintCode).orElse(SprintMetrics.none());
		return Result.success(new VelocityResponse(sprint.code(), sprint.status(), sprint.plannedVelocity(), sprint.velocity(),
				configs.find().velocityTrackingEnabled(), metrics));
	}

}
