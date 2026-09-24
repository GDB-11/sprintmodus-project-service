package com.sprintmodus.project_service.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sprintmodus.common_lib.result.Result;
import com.sprintmodus.project_service.application.dto.Responses.ProjectResponse;
import com.sprintmodus.project_service.application.error.ProjectError;
import com.sprintmodus.project_service.application.error.ProjectError.ProjectNotFound;
import com.sprintmodus.project_service.application.port.persistence.ProjectRepository;

@Service
public class GetProjectUseCase {

	private final ProjectRepository projects;

	public GetProjectUseCase(ProjectRepository projects) {
		this.projects = projects;
	}

	public Result<ProjectResponse, ProjectError> execute(UUID projectCode) {
		return projects.findByCode(projectCode).<Result<ProjectResponse, ProjectError>>map(project -> Result.success(ProjectResponse.from(project)))
				.orElseGet(() -> Result.failure(new ProjectNotFound()));
	}

}
