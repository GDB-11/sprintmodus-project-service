package com.sprintmodus.project_service.application.service;

import org.springframework.stereotype.Service;

import com.sprintmodus.common_lib.result.Result;
import com.sprintmodus.common_lib.tenant.TenantContext;
import com.sprintmodus.project_service.application.dto.Commands.CreateProject;
import com.sprintmodus.project_service.application.dto.Responses.ProjectResponse;
import com.sprintmodus.project_service.application.error.ProjectError;
import com.sprintmodus.project_service.application.error.ProjectError.InvalidProjectData;
import com.sprintmodus.project_service.application.error.ProjectError.ProjectKeyTaken;
import com.sprintmodus.project_service.application.error.ProjectError.ProjectLimitReached;
import com.sprintmodus.project_service.application.port.persistence.ProjectRepository;
import com.sprintmodus.project_service.application.port.persistence.ProjectRepository.NewProject;
import com.sprintmodus.project_service.domain.model.ProjectKey;

/**
 * Creates a project within the plan's limit. The limit is {@code maxProjects} from the caller's JWT (via the tenant
 * context), so no master-database call is needed; the count and the insert happen atomically in the repository.
 * <p>
 * If the caller gives no key, one is derived from the name ({@code WAR} for "Web App Rewrite") and made unique by
 * appending a number ({@code WAR2}, {@code WAR3}, ...).
 */
@Service
public class CreateProjectUseCase {

	static final int MAX_NAME_LENGTH = 255;

	static final int MAX_DESCRIPTION_LENGTH = 4000;

	private static final int MAX_KEY_ATTEMPTS = 50;

	private final ProjectRepository projects;

	public CreateProjectUseCase(ProjectRepository projects) {
		this.projects = projects;
	}

	public Result<ProjectResponse, ProjectError> execute(CreateProject command) {
		String name = command.name() == null ? "" : command.name().trim();
		if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
			return invalid("name", "Enter a project name of up to " + MAX_NAME_LENGTH + " characters.");
		}
		String description = command.description() == null || command.description().isBlank() ? null : command.description().trim();
		if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
			return invalid("description", "The description is too long.");
		}
		int maxProjects = TenantContext.getMaxProjects()
				.orElseThrow(() -> new IllegalStateException("The subscription limits are missing from the request context"));

		boolean chosenByCaller = command.key() != null && !command.key().isBlank();
		if (chosenByCaller) {
			var key = ProjectKey.parse(command.key());
			if (key.isFailure()) {
				return invalid("key", key.getError());
			}
			return create(new NewProject(name, description, key.getValue(), command.actor().userCode()), maxProjects);
		}

		String base = ProjectKey.suggestFrom(name);
		for (int attempt = 1; attempt <= MAX_KEY_ATTEMPTS; attempt++) {
			String candidate = ProjectKey.withSuffix(base, attempt);
			if (projects.existsByKey(candidate)) {
				continue;
			}
			var created = create(new NewProject(name, description, candidate, command.actor().userCode()), maxProjects);
			// Another request took the candidate between the check and the insert: try the next one
			if (created.isSuccess() || !(created.getError() instanceof ProjectKeyTaken)) {
				return created;
			}
		}
		return Result.failure(new ProjectKeyTaken());
	}

	private Result<ProjectResponse, ProjectError> create(NewProject project, int maxProjects) {
		return projects.createWithinLimit(project, maxProjects)
				.<ProjectError>mapError(rejection -> switch (rejection) {
					case LIMIT_REACHED -> new ProjectLimitReached(maxProjects);
					case KEY_TAKEN -> new ProjectKeyTaken();
				})
				.map(ProjectResponse::from);
	}

	private static Result<ProjectResponse, ProjectError> invalid(String field, String message) {
		return Result.failure(new InvalidProjectData(field, message));
	}

}
