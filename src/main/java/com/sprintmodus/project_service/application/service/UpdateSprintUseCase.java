package com.sprintmodus.project_service.application.service;

import org.springframework.stereotype.Service;

import com.sprintmodus.common_lib.result.Result;
import com.sprintmodus.project_service.application.dto.Commands.UpdateSprint;
import com.sprintmodus.project_service.application.dto.Responses.SprintResponse;
import com.sprintmodus.project_service.application.error.SprintError;
import com.sprintmodus.project_service.application.error.SprintError.InvalidSprintData;
import com.sprintmodus.project_service.application.error.SprintError.InvalidSprintState;
import com.sprintmodus.project_service.application.error.SprintError.SprintNotFound;
import com.sprintmodus.project_service.application.port.persistence.SprintRepository;
import com.sprintmodus.project_service.application.port.persistence.SprintRepository.SprintChanges;
import com.sprintmodus.project_service.domain.model.Sprint;
import com.sprintmodus.project_service.domain.model.SprintStatus;
import com.sprintmodus.project_service.domain.service.SprintSchedule;

/**
 * Renames a sprint, changes its planned velocity, or moves it. Moving keeps the sprint's own length
 * ({@code configuredDays}), so the end date follows, and is only possible before the sprint starts. A closed sprint is
 * history and cannot be changed.
 */
@Service
public class UpdateSprintUseCase {

	private final SprintRepository sprints;

	public UpdateSprintUseCase(SprintRepository sprints) {
		this.sprints = sprints;
	}

	public Result<SprintResponse, SprintError> execute(UpdateSprint command) {
		String name = command.name() == null ? "" : command.name().trim();
		if (name.isEmpty() || name.length() > CreateSprintUseCase.MAX_NAME_LENGTH) {
			return Result.failure(new InvalidSprintData("name",
					"Enter a sprint name of up to " + CreateSprintUseCase.MAX_NAME_LENGTH + " characters."));
		}
		if (command.plannedVelocity() != null
				&& (command.plannedVelocity() < 0 || command.plannedVelocity() > CreateSprintUseCase.MAX_PLANNED_VELOCITY)) {
			return Result.failure(new InvalidSprintData("plannedVelocity", "The planned velocity must be zero or more."));
		}
		Sprint current = sprints.findByCode(command.sprintCode()).orElse(null);
		if (current == null) {
			return Result.failure(new SprintNotFound());
		}
		if (current.status() == SprintStatus.CLOSED) {
			return Result.failure(new InvalidSprintState("A closed sprint cannot be changed."));
		}
		boolean moves = command.startDate() != null && !command.startDate().equals(current.startDate());
		if (moves && current.status() != SprintStatus.PLANNED) {
			return Result.failure(new InvalidSprintState("The dates of a sprint can only be changed before it starts."));
		}

		var start = moves ? command.startDate() : current.startDate();
		var end = moves ? SprintSchedule.endDate(start, current.configuredDays()) : current.endDate();
		int plannedVelocity = command.plannedVelocity() != null ? command.plannedVelocity() : current.plannedVelocity();

		return sprints.update(new SprintChanges(current.code(), name, start, end, plannedVelocity))
				.mapError(SprintErrors::from).map(SprintResponse::from);
	}

}
