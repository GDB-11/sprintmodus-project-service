package com.sprintmodus.project_service.application.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sprintmodus.common_lib.result.Result;
import com.sprintmodus.project_service.application.error.SprintError;
import com.sprintmodus.project_service.application.error.SprintError.SprintNotFound;
import com.sprintmodus.project_service.application.port.persistence.BurndownRepository;
import com.sprintmodus.project_service.application.port.persistence.SprintRepository;
import com.sprintmodus.project_service.domain.model.Burndown;
import com.sprintmodus.project_service.domain.service.BurndownSeries;

/** The burndown of a sprint: what was recorded, with the quiet days filled in, next to the ideal line. Any member may read it. */
@Service
public class GetSprintBurndownUseCase {

	private final SprintRepository sprints;

	private final BurndownRepository burndown;

	private final Clock clock;

	public GetSprintBurndownUseCase(SprintRepository sprints, BurndownRepository burndown, Clock clock) {
		this.sprints = sprints;
		this.burndown = burndown;
		this.clock = clock;
	}

	public Result<Burndown, SprintError> execute(UUID sprintCode) {
		var sprint = sprints.findByCode(sprintCode).orElse(null);
		if (sprint == null) {
			return Result.failure(new SprintNotFound());
		}
		return Result.success(BurndownSeries.build(sprint, burndown.findBySprint(sprintCode), LocalDate.now(clock)));
	}

}
