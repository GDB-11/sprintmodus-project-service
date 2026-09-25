package com.sprintmodus.project_service.application.service;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.sprintmodus.common_lib.result.Result;
import com.sprintmodus.project_service.application.dto.Commands.CreateSprint;
import com.sprintmodus.project_service.application.dto.Responses.SprintResponse;
import com.sprintmodus.project_service.application.error.SprintError;
import com.sprintmodus.project_service.application.error.SprintError.InvalidSprintData;
import com.sprintmodus.project_service.application.error.SprintError.NotAllowed;
import com.sprintmodus.project_service.application.port.persistence.SprintConfigRepository;
import com.sprintmodus.project_service.application.port.persistence.SprintRepository;
import com.sprintmodus.project_service.application.port.persistence.SprintRepository.NewSprint;
import com.sprintmodus.project_service.domain.model.SprintConfig;
import com.sprintmodus.project_service.domain.service.SprintSchedule;

/**
 * Creates a sprint (owners and admins only) whose length is the tenant's configured one: {@code endDate = startDate + defaultSprintDays}. Without
 * a start date the first configured start day (today or later) is used. Sprints of a project must not overlap; the
 * repository checks that atomically with the insert.
 */
@Service
public class CreateSprintUseCase {

	static final int MAX_NAME_LENGTH = 255;

	static final int MAX_PLANNED_VELOCITY = 100_000;

	private final SprintRepository sprints;

	private final SprintConfigRepository configs;

	private final Clock clock;

	public CreateSprintUseCase(SprintRepository sprints, SprintConfigRepository configs, Clock clock) {
		this.sprints = sprints;
		this.configs = configs;
		this.clock = clock;
	}

	public Result<SprintResponse, SprintError> execute(CreateSprint command) {
		if (!command.actor().canAdminister()) {
			return Result.failure(new NotAllowed());
		}
		if (command.projectCode() == null) {
			return Result.failure(new InvalidSprintData("projectCode", "Choose the project of the sprint."));
		}
		String name = command.name() == null ? "" : command.name().trim();
		if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
			return Result.failure(new InvalidSprintData("name", "Enter a sprint name of up to " + MAX_NAME_LENGTH + " characters."));
		}
		int plannedVelocity = command.plannedVelocity() == null ? 0 : command.plannedVelocity();
		if (plannedVelocity < 0 || plannedVelocity > MAX_PLANNED_VELOCITY) {
			return Result.failure(new InvalidSprintData("plannedVelocity", "The planned velocity must be zero or more."));
		}

		SprintConfig config = configs.find();
		LocalDate start = command.startDate() != null ? command.startDate() : config.nextStart(LocalDate.now(clock));
		LocalDate end = SprintSchedule.endDate(start, config.defaultSprintDays());

		return sprints.create(new NewSprint(command.projectCode(), name, config.defaultSprintDays(), start, end, plannedVelocity,
				command.actor().userCode())).mapError(SprintErrors::from).map(SprintResponse::from);
	}

}
