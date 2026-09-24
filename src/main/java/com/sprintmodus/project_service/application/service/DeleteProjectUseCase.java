package com.sprintmodus.project_service.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sprintmodus.common_lib.result.Result;
import com.sprintmodus.common_lib.result.Unit;
import com.sprintmodus.project_service.application.dto.Actor;
import com.sprintmodus.project_service.application.error.ProjectError;
import com.sprintmodus.project_service.application.error.ProjectError.NotAllowed;
import com.sprintmodus.project_service.application.error.ProjectError.ProjectNotFound;
import com.sprintmodus.project_service.application.port.persistence.ProjectRepository;

/**
 * Soft-deletes a project and its sprints; only owners and admins may. The slot it used counts against the plan's limit
 * again, but its key stays reserved.
 */
@Service
public class DeleteProjectUseCase {

	private final ProjectRepository projects;

	public DeleteProjectUseCase(ProjectRepository projects) {
		this.projects = projects;
	}

	public Result<Unit, ProjectError> execute(Actor actor, UUID projectCode) {
		if (!actor.canAdminister()) {
			return Result.failure(new NotAllowed());
		}
		return projects.softDelete(projectCode) ? Result.success(Unit.VALUE) : Result.failure(new ProjectNotFound());
	}

}
