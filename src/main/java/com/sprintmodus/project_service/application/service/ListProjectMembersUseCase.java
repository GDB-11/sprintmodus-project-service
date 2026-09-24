package com.sprintmodus.project_service.application.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sprintmodus.common_lib.result.Result;
import com.sprintmodus.project_service.application.dto.Responses.MemberResponse;
import com.sprintmodus.project_service.application.error.ProjectError;
import com.sprintmodus.project_service.application.error.ProjectError.ProjectNotFound;
import com.sprintmodus.project_service.application.port.persistence.ProjectMemberRepository;
import com.sprintmodus.project_service.application.port.persistence.ProjectRepository;

@Service
public class ListProjectMembersUseCase {

	private final ProjectRepository projects;

	private final ProjectMemberRepository members;

	public ListProjectMembersUseCase(ProjectRepository projects, ProjectMemberRepository members) {
		this.projects = projects;
		this.members = members;
	}

	public Result<List<MemberResponse>, ProjectError> execute(UUID projectCode) {
		if (projects.findByCode(projectCode).isEmpty()) {
			return Result.failure(new ProjectNotFound());
		}
		return Result.success(members.findByProject(projectCode).stream().map(MemberResponse::from).toList());
	}

}
