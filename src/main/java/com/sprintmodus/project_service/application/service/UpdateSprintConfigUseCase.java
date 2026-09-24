package com.sprintmodus.project_service.application.service;

import org.springframework.stereotype.Service;

import com.sprintmodus.common_lib.result.Result;
import com.sprintmodus.project_service.application.dto.Commands.UpdateSprintConfig;
import com.sprintmodus.project_service.application.dto.Responses.SprintConfigResponse;
import com.sprintmodus.project_service.application.error.SprintConfigError;
import com.sprintmodus.project_service.application.error.SprintConfigError.InvalidSprintConfig;
import com.sprintmodus.project_service.application.error.SprintConfigError.NotAllowed;
import com.sprintmodus.project_service.application.port.persistence.SprintConfigRepository;
import com.sprintmodus.project_service.domain.model.SprintConfig;

/**
 * Changes the tenant's sprint settings; only owners and admins may. It affects sprints created afterwards: an existing
 * sprint keeps the length it was created with.
 */
@Service
public class UpdateSprintConfigUseCase {

	private final SprintConfigRepository configs;

	public UpdateSprintConfigUseCase(SprintConfigRepository configs) {
		this.configs = configs;
	}

	public Result<SprintConfigResponse, SprintConfigError> execute(UpdateSprintConfig command) {
		if (!command.actor().canAdminister()) {
			return Result.failure(new NotAllowed());
		}
		return SprintConfig.validated(command.defaultSprintDays(), command.sprintStartDay(), command.velocityTrackingEnabled())
				.<SprintConfigError>mapError(message -> new InvalidSprintConfig("sprintConfig", message))
				.map(configs::save).map(SprintConfigResponse::from);
	}

}
