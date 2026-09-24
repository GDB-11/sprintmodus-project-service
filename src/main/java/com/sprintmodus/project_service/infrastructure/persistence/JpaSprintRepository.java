package com.sprintmodus.project_service.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.sprintmodus.common_lib.result.Result;
import com.sprintmodus.project_service.application.port.persistence.SprintRepository;
import com.sprintmodus.project_service.domain.model.Sprint;
import com.sprintmodus.project_service.domain.model.SprintMetrics;
import com.sprintmodus.project_service.domain.model.SprintStatus;

/**
 * The {@link SprintRepository} port over the native queries of {@link SprintQueries}. Every change that depends on the
 * project's other sprints (overlap, one active sprint) first locks the project's row, so two requests for the same
 * project are serialized and the checks cannot be outrun.
 */
@Repository
class JpaSprintRepository implements SprintRepository {

	private final SprintQueries sprints;

	private final ProjectQueries projects;

	private final SprintOverviewQueries overview;

	private final TransactionTemplate transaction;

	JpaSprintRepository(SprintQueries sprints, ProjectQueries projects, SprintOverviewQueries overview,
			PlatformTransactionManager transactionManager) {
		this.sprints = sprints;
		this.projects = projects;
		this.overview = overview;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	@Override
	public Result<Sprint, Rejection> create(NewSprint sprint) {
		return transaction.execute(_ -> {
			String projectCode = sprint.projectCode().toString();
			if (projects.lockByCode(projectCode).isEmpty()) {
				return Result.failure(Rejection.PROJECT_NOT_FOUND);
			}
			if (sprints.countOverlapping(projectCode, sprint.startDate(), sprint.endDate()) > 0) {
				return Result.failure(Rejection.OVERLAP);
			}
			String code = UUID.randomUUID().toString();
			int inserted = sprints.insert(code, projectCode, sprint.name(), sprint.configuredDays(), sprint.startDate(),
					sprint.endDate(), sprint.plannedVelocity(), sprint.createdBy().toString());
			if (inserted == 0) {
				throw new IllegalStateException("The creating user is not an active member of the tenant");
			}
			return Result.success(toDomain(sprints.findByCode(code).orElseThrow()));
		});
	}

	@Override
	public Optional<Sprint> findByCode(UUID code) {
		return sprints.findByCode(code.toString()).map(JpaSprintRepository::toDomain);
	}

	@Override
	public List<Sprint> findByProject(UUID projectCode) {
		return sprints.findByProject(projectCode.toString()).stream().map(JpaSprintRepository::toDomain).toList();
	}

	@Override
	public Result<Sprint, Rejection> update(SprintChanges changes) {
		return transaction.execute(_ -> {
			Sprint current = findByCode(changes.sprintCode()).orElse(null);
			if (current == null) {
				return Result.failure(Rejection.SPRINT_NOT_FOUND);
			}
			if (current.status() == SprintStatus.CLOSED) {
				return Result.failure(Rejection.CLOSED);
			}
			String projectCode = current.projectCode().toString();
			projects.lockByCode(projectCode);
			if (sprints.countOverlappingExcept(projectCode, current.code().toString(), changes.startDate(), changes.endDate()) > 0) {
				return Result.failure(Rejection.OVERLAP);
			}
			int updated = sprints.updateDetails(current.code().toString(), changes.name(), changes.startDate(), changes.endDate(),
					changes.plannedVelocity());
			if (updated == 0) {
				// closed by another request since the read above
				return Result.failure(Rejection.CLOSED);
			}
			return Result.success(findByCode(current.code()).orElseThrow());
		});
	}

	@Override
	public Result<Sprint, Rejection> start(UUID sprintCode) {
		return transaction.execute(_ -> {
			Sprint current = findByCode(sprintCode).orElse(null);
			if (current == null) {
				return Result.failure(Rejection.SPRINT_NOT_FOUND);
			}
			String projectCode = current.projectCode().toString();
			projects.lockByCode(projectCode);
			// Read again under the lock: the status may have changed while waiting for it
			Sprint locked = findByCode(sprintCode).orElse(null);
			if (locked == null) {
				return Result.failure(Rejection.SPRINT_NOT_FOUND);
			}
			if (locked.status() != SprintStatus.PLANNED) {
				return Result.failure(Rejection.NOT_PLANNED);
			}
			if (sprints.countActiveInProject(projectCode) > 0) {
				return Result.failure(Rejection.ANOTHER_ACTIVE);
			}
			sprints.start(sprintCode.toString());
			return Result.success(findByCode(sprintCode).orElseThrow());
		});
	}

	@Override
	public boolean close(UUID sprintCode) {
		return transaction.execute(_ -> sprints.close(sprintCode.toString()) > 0);
	}

	@Override
	public boolean updateVelocity(UUID sprintCode, int velocity) {
		return transaction.execute(_ -> sprints.updateVelocity(sprintCode.toString(), velocity) > 0);
	}

	@Override
	public Optional<SprintMetrics> findMetrics(UUID sprintCode) {
		return overview.findByCode(sprintCode.toString()).map(entity -> new SprintMetrics(entity.totalItems, entity.completedItems,
				entity.plannedEffort, entity.completedEffort, entity.defectCount));
	}

	private static Sprint toDomain(SprintEntity entity) {
		return new Sprint(entity.code, entity.projectCode, entity.name, entity.status, entity.configuredDays, entity.startDate,
				entity.endDate, entity.plannedVelocity, entity.velocity);
	}

}
