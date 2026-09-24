package com.sprintmodus.project_service.application.port.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.sprintmodus.common_lib.result.Result;
import com.sprintmodus.project_service.domain.model.Project;

/**
 * Projects of the current tenant (the database is chosen by the tenant context, which the request's security filter sets).
 */
public interface ProjectRepository {

	enum Rejection {

		LIMIT_REACHED,
		KEY_TAKEN

	}

	record NewProject(String name, String description, String key, UUID createdBy) {
	}

	/** Whether the key is used by any project, deleted ones included: a key stays reserved. */
	boolean existsByKey(String key);

	/**
	 * Creates the project, its work item sequence (seeded at 1000) and the creator's membership, in one transaction that
	 * also checks the limit: concurrent creations are serialized, so the limit can never be exceeded.
	 */
	Result<Project, Rejection> createWithinLimit(NewProject project, int maxProjects);

	Optional<Project> findByCode(UUID code);

	List<Project> findAllActive();

	Optional<Project> updateDetails(UUID code, String name, String description);

	/** Soft-deletes the project and its sprints. Returns whether an active project was deleted. */
	boolean softDelete(UUID code);

}
