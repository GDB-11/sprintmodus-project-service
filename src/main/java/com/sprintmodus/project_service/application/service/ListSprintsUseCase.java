package com.sprintmodus.project_service.application.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sprintmodus.common_lib.result.Result;
import com.sprintmodus.project_service.application.dto.Responses.SprintResponse;
import com.sprintmodus.project_service.application.error.SprintError;
import com.sprintmodus.project_service.application.error.SprintError.ProjectNotFound;
import com.sprintmodus.project_service.application.port.persistence.ProjectRepository;
import com.sprintmodus.project_service.application.port.persistence.SprintRepository;

@Service
public class ListSprintsUseCase {

	private final ProjectRepository projects;

	private final SprintRepository sprints;

	public ListSprintsUseCase(ProjectRepository projects, SprintRepository sprints) {
		this.projects = projects;
		this.sprints = sprints;
	}

	public Result<List<SprintResponse>, SprintError> execute(UUID projectCode) {
		if (projects.findByCode(projectCode).isEmpty()) {
			return Result.failure(new ProjectNotFound());
		}
		return Result.success(sprints.findByProject(projectCode).stream().map(SprintResponse::from).toList());
	}

}
