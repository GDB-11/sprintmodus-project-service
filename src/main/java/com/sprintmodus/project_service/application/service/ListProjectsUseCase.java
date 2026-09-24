package com.sprintmodus.project_service.application.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.sprintmodus.common_lib.result.Result;
import com.sprintmodus.project_service.application.dto.Responses.ProjectResponse;
import com.sprintmodus.project_service.application.error.ProjectError;
import com.sprintmodus.project_service.application.port.persistence.ProjectRepository;

@Service
public class ListProjectsUseCase {

	private final ProjectRepository projects;

	public ListProjectsUseCase(ProjectRepository projects) {
		this.projects = projects;
	}

	public Result<List<ProjectResponse>, ProjectError> execute() {
		return Result.success(projects.findAllActive().stream().map(ProjectResponse::from).toList());
	}

}
