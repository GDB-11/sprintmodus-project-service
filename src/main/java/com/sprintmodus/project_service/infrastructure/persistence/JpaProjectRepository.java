package com.sprintmodus.project_service.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.sprintmodus.common_lib.result.Result;
import com.sprintmodus.project_service.application.port.persistence.ProjectRepository;
import com.sprintmodus.project_service.domain.model.Project;

/**
 * The {@link ProjectRepository} port over the native queries of {@link ProjectQueries} and its neighbours. The tenant's
 * database is chosen by the tenant context, which the request's security filter has set before any transaction begins.
 */
@Repository
class JpaProjectRepository implements ProjectRepository {

	private final ProjectQueries projects;

	private final WorkItemSequenceQueries sequences;

	private final ProjectMemberQueries members;

	private final TenantSprintConfigQueries configs;

	private final SprintQueries sprints;

	private final TransactionTemplate transaction;

	JpaProjectRepository(ProjectQueries projects, WorkItemSequenceQueries sequences, ProjectMemberQueries members,
			TenantSprintConfigQueries configs, SprintQueries sprints, PlatformTransactionManager transactionManager) {
		this.projects = projects;
		this.sequences = sequences;
		this.members = members;
		this.configs = configs;
		this.sprints = sprints;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	@Override
	public boolean existsByKey(String key) {
		return projects.countByKey(key) > 0;
	}

	@Override
	public Result<Project, Rejection> createWithinLimit(NewProject project, int maxProjects) {
		try {
			return transaction.execute(_ -> create(project, maxProjects));
		}
		catch (DataIntegrityViolationException e) {
			// The transaction below already serializes creators, so this is the database's last line of defence
			if (String.valueOf(e.getMostSpecificCause().getMessage()).contains("uk_project_key")) {
				return Result.failure(Rejection.KEY_TAKEN);
			}
			throw e;
		}
	}

	private Result<Project, Rejection> create(NewProject project, int maxProjects) {
		// Everything that creates a project takes this lock first, so the count below cannot be outrun by another request
		configs.lockForUpdate().orElseThrow(() -> new IllegalStateException("The tenant has no sprint configuration row"));
		if (projects.countActive() >= maxProjects) {
			return Result.failure(Rejection.LIMIT_REACHED);
		}
		if (projects.countByKey(project.key()) > 0) {
			return Result.failure(Rejection.KEY_TAKEN);
		}

		String code = UUID.randomUUID().toString();
		int inserted = projects.insert(code, project.name(), orEmpty(project.description()), project.key(),
				project.createdBy().toString());
		if (inserted == 0) {
			throw new IllegalStateException("The creating user is not an active member of the tenant");
		}
		// The counter work items are numbered from (first item is 1000), and the creator joins the project
		sequences.insertForProject(code);
		members.insert(code, project.createdBy().toString());
		return Result.success(toDomain(projects.findByCode(code).orElseThrow()));
	}

	@Override
	public Optional<Project> findByCode(UUID code) {
		return projects.findByCode(code.toString()).map(JpaProjectRepository::toDomain);
	}

	@Override
	public List<Project> findAllActive() {
		return projects.findAllActive().stream().map(JpaProjectRepository::toDomain).toList();
	}

	@Override
	public Optional<Project> updateDetails(UUID code, String name, String description) {
		return transaction.execute(_ -> projects.updateDetails(code.toString(), name, orEmpty(description)) == 0
				? Optional.<Project>empty() : findByCode(code));
	}

	@Override
	public boolean softDelete(UUID code) {
		return Boolean.TRUE.equals(transaction.execute(_ -> {
			if (projects.softDelete(code.toString()) == 0) {
				return false;
			}
			sprints.softDeleteByProject(code.toString());
			return true;
		}));
	}

	private static String orEmpty(String value) {
		return value == null ? "" : value;
	}

	private static Project toDomain(ProjectEntity entity) {
		return new Project(entity.code, entity.name, entity.description, entity.key, entity.createdByCode, entity.createdAt);
	}

}
