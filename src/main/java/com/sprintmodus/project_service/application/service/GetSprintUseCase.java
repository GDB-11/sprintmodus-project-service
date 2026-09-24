package com.sprintmodus.project_service.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sprintmodus.common_lib.result.Result;
import com.sprintmodus.project_service.application.dto.Responses.SprintResponse;
import com.sprintmodus.project_service.application.error.SprintError;
import com.sprintmodus.project_service.application.error.SprintError.SprintNotFound;
import com.sprintmodus.project_service.application.port.persistence.SprintRepository;

@Service
public class GetSprintUseCase {

	private final SprintRepository sprints;

	public GetSprintUseCase(SprintRepository sprints) {
		this.sprints = sprints;
	}

	public Result<SprintResponse, SprintError> execute(UUID sprintCode) {
		return sprints.findByCode(sprintCode).<Result<SprintResponse, SprintError>>map(sprint -> Result.success(SprintResponse.from(sprint)))
				.orElseGet(() -> Result.failure(new SprintNotFound()));
	}

}
