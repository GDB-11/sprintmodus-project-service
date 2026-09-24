package com.sprintmodus.project_service.application.service;

import org.springframework.stereotype.Service;

import com.sprintmodus.common_lib.result.Result;
import com.sprintmodus.project_service.application.dto.Responses.SprintConfigResponse;
import com.sprintmodus.project_service.application.error.SprintConfigError;
import com.sprintmodus.project_service.application.port.persistence.SprintConfigRepository;

@Service
public class GetSprintConfigUseCase {

	private final SprintConfigRepository configs;

	public GetSprintConfigUseCase(SprintConfigRepository configs) {
		this.configs = configs;
	}

	public Result<SprintConfigResponse, SprintConfigError> execute() {
		return Result.success(SprintConfigResponse.from(configs.find()));
	}

}
