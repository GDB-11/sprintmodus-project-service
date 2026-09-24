package com.sprintmodus.project_service.adapter.rest.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sprintmodus.common_lib.security.AuthenticatedUser;
import com.sprintmodus.project_service.adapter.rest.dto.Requests;
import com.sprintmodus.project_service.adapter.rest.dto.Responses;
import com.sprintmodus.project_service.application.dto.Commands.CreateProject;
import com.sprintmodus.project_service.application.dto.Commands.UpdateProject;
import com.sprintmodus.project_service.application.service.CreateProjectUseCase;
import com.sprintmodus.project_service.application.service.DeleteProjectUseCase;
import com.sprintmodus.project_service.application.service.GetProjectUseCase;
import com.sprintmodus.project_service.application.service.ListProjectMembersUseCase;
import com.sprintmodus.project_service.application.service.ListProjectsUseCase;
import com.sprintmodus.project_service.application.service.UpdateProjectUseCase;

/** Maps HTTP to the project use cases and their {@code Result}s back to HTTP. It holds no business logic. */
@RestController
@RequestMapping("/api/projects")
class ProjectController {

	private final CreateProjectUseCase createProject;

	private final ListProjectsUseCase listProjects;

	private final GetProjectUseCase getProject;

	private final UpdateProjectUseCase updateProject;

	private final DeleteProjectUseCase deleteProject;

	private final ListProjectMembersUseCase listMembers;

	ProjectController(CreateProjectUseCase createProject, ListProjectsUseCase listProjects, GetProjectUseCase getProject,
			UpdateProjectUseCase updateProject, DeleteProjectUseCase deleteProject, ListProjectMembersUseCase listMembers) {
		this.createProject = createProject;
		this.listProjects = listProjects;
		this.getProject = getProject;
		this.updateProject = updateProject;
		this.deleteProject = deleteProject;
		this.listMembers = listMembers;
	}

	@GetMapping
	ResponseEntity<?> list() {
		return listProjects.execute().fold(projects -> ResponseEntity.ok(projects.stream().map(Responses.Project::from).toList()),
				ErrorMapper::toResponse);
	}

	@PostMapping
	ResponseEntity<?> create(@AuthenticationPrincipal AuthenticatedUser user, @RequestBody Requests.CreateProject request) {
		return createProject.execute(new CreateProject(Actors.from(user), request.name(), request.description(), request.key()))
				.fold(project -> ResponseEntity.status(HttpStatus.CREATED).body(Responses.Project.from(project)), ErrorMapper::toResponse);
	}

	@GetMapping("/{projectCode}")
	ResponseEntity<?> get(@PathVariable UUID projectCode) {
		return getProject.execute(projectCode).fold(project -> ResponseEntity.ok(Responses.Project.from(project)), ErrorMapper::toResponse);
	}

	@PutMapping("/{projectCode}")
	ResponseEntity<?> update(@PathVariable UUID projectCode, @RequestBody Requests.UpdateProject request) {
		return updateProject.execute(new UpdateProject(projectCode, request.name(), request.description()))
				.fold(project -> ResponseEntity.ok(Responses.Project.from(project)), ErrorMapper::toResponse);
	}

	@DeleteMapping("/{projectCode}")
	ResponseEntity<?> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectCode) {
		return deleteProject.execute(Actors.from(user), projectCode).fold(_ -> ResponseEntity.noContent().build(),
				ErrorMapper::toResponse);
	}

	@GetMapping("/{projectCode}/members")
	ResponseEntity<?> members(@PathVariable UUID projectCode) {
		return listMembers.execute(projectCode).fold(members -> ResponseEntity.ok(members.stream().map(Responses.Member::from).toList()),
				ErrorMapper::toResponse);
	}

}
