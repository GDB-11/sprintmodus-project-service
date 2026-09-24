package com.sprintmodus.project_service.application.service;

import org.springframework.stereotype.Service;

import com.sprintmodus.common_lib.result.Result;
import com.sprintmodus.project_service.application.dto.Commands.UpdateProject;
import com.sprintmodus.project_service.application.dto.Responses.ProjectResponse;
import com.sprintmodus.project_service.application.error.ProjectError;
import com.sprintmodus.project_service.application.error.ProjectError.InvalidProjectData;
import com.sprintmodus.project_service.application.error.ProjectError.ProjectNotFound;
import com.sprintmodus.project_service.application.port.persistence.ProjectRepository;

/** Renames a project or changes its description. The key never changes: work items are known by it. */
@Service
public class UpdateProjectUseCase {

	private final ProjectRepository projects;

	public UpdateProjectUseCase(ProjectRepository projects) {
		this.projects = projects;
	}

	public Result<ProjectResponse, ProjectError> execute(UpdateProject command) {
		String name = command.name() == null ? "" : command.name().trim();
		if (name.isEmpty() || name.length() > CreateProjectUseCase.MAX_NAME_LENGTH) {
			return Result.failure(new InvalidProjectData("name",
					"Enter a project name of up to " + CreateProjectUseCase.MAX_NAME_LENGTH + " characters."));
		}
		String description = command.description() == null || command.description().isBlank() ? null : command.description().trim();
		if (description != null && description.length() > CreateProjectUseCase.MAX_DESCRIPTION_LENGTH) {
			return Result.failure(new InvalidProjectData("description", "The description is too long."));
		}
		return projects.updateDetails(command.projectCode(), name, description)
				.<Result<ProjectResponse, ProjectError>>map(project -> Result.success(ProjectResponse.from(project)))
				.orElseGet(() -> Result.failure(new ProjectNotFound()));
	}

}
