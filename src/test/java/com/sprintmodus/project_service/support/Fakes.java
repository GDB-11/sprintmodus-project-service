package com.sprintmodus.project_service.support;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.sprintmodus.common_lib.result.Result;
import com.sprintmodus.project_service.application.port.persistence.ProjectMemberRepository;
import com.sprintmodus.project_service.application.port.persistence.ProjectRepository;
import com.sprintmodus.project_service.application.port.persistence.SprintConfigRepository;
import com.sprintmodus.project_service.application.port.persistence.SprintRepository;
import com.sprintmodus.project_service.domain.model.OrganizationRole;
import com.sprintmodus.project_service.domain.model.Project;
import com.sprintmodus.project_service.domain.model.ProjectMember;
import com.sprintmodus.project_service.domain.model.Sprint;
import com.sprintmodus.project_service.domain.model.SprintConfig;
import com.sprintmodus.project_service.domain.model.SprintMetrics;
import com.sprintmodus.project_service.domain.model.SprintStatus;
import com.sprintmodus.project_service.domain.service.SprintSchedule;

/** In-memory implementations of the persistence ports, so use cases can be tested without Spring or a database. */
public final class Fakes {

	private Fakes() {
	}

	public static class Projects implements ProjectRepository {

		public final List<Project> stored = new ArrayList<>();

		/** Codes of deleted projects: their keys stay reserved. */
		public final List<String> reservedKeys = new ArrayList<>();

		public final List<ProjectMember> members = new ArrayList<>();

		/** How many times {@link #createWithinLimit} loses a key race before it works. */
		public int keyRaces;

		public int maxSeen = -1;

		@Override
		public boolean existsByKey(String key) {
			return reservedKeys.contains(key) || stored.stream().anyMatch(project -> project.key().equals(key));
		}

		@Override
		public Result<Project, Rejection> createWithinLimit(NewProject project, int maxProjects) {
			maxSeen = maxProjects;
			if (stored.size() >= maxProjects) {
				return Result.failure(Rejection.LIMIT_REACHED);
			}
			if (keyRaces > 0) {
				keyRaces--;
				reservedKeys.add(project.key());
				return Result.failure(Rejection.KEY_TAKEN);
			}
			if (existsByKey(project.key())) {
				return Result.failure(Rejection.KEY_TAKEN);
			}
			Project created = new Project(UUID.randomUUID(), project.name(), project.description(), project.key(), project.createdBy(),
					Instant.parse("2026-09-23T12:00:00Z"));
			stored.add(created);
			return Result.success(created);
		}

		@Override
		public Optional<Project> findByCode(UUID code) {
			return stored.stream().filter(project -> project.code().equals(code)).findFirst();
		}

		@Override
		public List<Project> findAllActive() {
			return List.copyOf(stored);
		}

		@Override
		public Optional<Project> updateDetails(UUID code, String name, String description) {
			Optional<Project> existing = findByCode(code);
			existing.ifPresent(project -> {
				stored.remove(project);
				stored.add(new Project(code, name, description, project.key(), project.createdBy(), project.createdAt()));
			});
			return findByCode(code);
		}

		@Override
		public boolean softDelete(UUID code) {
			Optional<Project> existing = findByCode(code);
			existing.ifPresent(project -> {
				stored.remove(project);
				reservedKeys.add(project.key());
			});
			return existing.isPresent();
		}

	}

	public static class Members implements ProjectMemberRepository {

		public final List<ProjectMember> members = new ArrayList<>();

		@Override
		public List<ProjectMember> findByProject(UUID projectCode) {
			return List.copyOf(members);
		}

		public void add(String name, OrganizationRole role) {
			members.add(new ProjectMember(UUID.randomUUID(), name, name.toLowerCase(Locale.ROOT).replace(' ', '.') + "@acme.io", role));
		}

	}

	/** Mimics the real repository's rules: overlap, one active sprint per project, frozen closed sprints. */
	public static class Sprints implements SprintRepository {

		public final List<Sprint> stored = new ArrayList<>();

		public final List<UUID> knownProjects = new ArrayList<>();

		public SprintMetrics metrics = SprintMetrics.none();

		public UUID addProject() {
			UUID code = UUID.randomUUID();
			knownProjects.add(code);
			return code;
		}

		public Sprint add(UUID projectCode, String name, SprintStatus status, LocalDate start, int days) {
			Sprint sprint = new Sprint(UUID.randomUUID(), projectCode, name, status, days, start, start.plusDays(days), 0, 0);
			stored.add(sprint);
			return sprint;
		}

		private boolean overlaps(UUID projectCode, LocalDate start, LocalDate end, UUID except) {
			return stored.stream().filter(sprint -> sprint.projectCode().equals(projectCode))
					.filter(sprint -> !sprint.code().equals(except))
					.anyMatch(sprint -> SprintSchedule.overlap(sprint.startDate(), sprint.endDate(), start, end));
		}

		private void replace(Sprint before, Sprint after) {
			stored.set(stored.indexOf(before), after);
		}

		@Override
		public Result<Sprint, Rejection> create(NewSprint sprint) {
			if (!knownProjects.contains(sprint.projectCode())) {
				return Result.failure(Rejection.PROJECT_NOT_FOUND);
			}
			if (overlaps(sprint.projectCode(), sprint.startDate(), sprint.endDate(), null)) {
				return Result.failure(Rejection.OVERLAP);
			}
			Sprint created = new Sprint(UUID.randomUUID(), sprint.projectCode(), sprint.name(), SprintStatus.PLANNED,
					sprint.configuredDays(), sprint.startDate(), sprint.endDate(), sprint.plannedVelocity(), 0);
			stored.add(created);
			return Result.success(created);
		}

		@Override
		public Optional<Sprint> findByCode(UUID code) {
			return stored.stream().filter(sprint -> sprint.code().equals(code)).findFirst();
		}

		@Override
		public List<Sprint> findByProject(UUID projectCode) {
			return stored.stream().filter(sprint -> sprint.projectCode().equals(projectCode)).toList();
		}

		@Override
		public Result<Sprint, Rejection> update(SprintChanges changes) {
			Sprint before = findByCode(changes.sprintCode()).orElse(null);
			if (before == null) {
				return Result.failure(Rejection.SPRINT_NOT_FOUND);
			}
			if (before.status() == SprintStatus.CLOSED) {
				return Result.failure(Rejection.CLOSED);
			}
			if (overlaps(before.projectCode(), changes.startDate(), changes.endDate(), before.code())) {
				return Result.failure(Rejection.OVERLAP);
			}
			Sprint after = new Sprint(before.code(), before.projectCode(), changes.name(), before.status(), before.configuredDays(),
					changes.startDate(), changes.endDate(), changes.plannedVelocity(), before.velocity());
			replace(before, after);
			return Result.success(after);
		}

		@Override
		public Result<Sprint, Rejection> start(UUID sprintCode) {
			Sprint before = findByCode(sprintCode).orElse(null);
			if (before == null) {
				return Result.failure(Rejection.SPRINT_NOT_FOUND);
			}
			if (before.status() != SprintStatus.PLANNED) {
				return Result.failure(Rejection.NOT_PLANNED);
			}
			if (findByProject(before.projectCode()).stream().anyMatch(sprint -> sprint.status() == SprintStatus.ACTIVE)) {
				return Result.failure(Rejection.ANOTHER_ACTIVE);
			}
			Sprint after = new Sprint(before.code(), before.projectCode(), before.name(), SprintStatus.ACTIVE, before.configuredDays(),
					before.startDate(), before.endDate(), before.plannedVelocity(), before.velocity());
			replace(before, after);
			return Result.success(after);
		}

		@Override
		public boolean close(UUID sprintCode) {
			Sprint before = findByCode(sprintCode).orElse(null);
			if (before == null || before.status() != SprintStatus.ACTIVE) {
				return false;
			}
			replace(before, new Sprint(before.code(), before.projectCode(), before.name(), SprintStatus.CLOSED, before.configuredDays(),
					before.startDate(), before.endDate(), before.plannedVelocity(), before.velocity()));
			return true;
		}

		@Override
		public boolean updateVelocity(UUID sprintCode, int velocity) {
			Sprint before = findByCode(sprintCode).orElse(null);
			if (before == null || before.status() == SprintStatus.CLOSED) {
				return false;
			}
			replace(before, new Sprint(before.code(), before.projectCode(), before.name(), before.status(), before.configuredDays(),
					before.startDate(), before.endDate(), before.plannedVelocity(), velocity));
			return true;
		}

		@Override
		public Optional<SprintMetrics> findMetrics(UUID sprintCode) {
			return findByCode(sprintCode).map(_ -> metrics);
		}

	}

	public static class Configs implements SprintConfigRepository {

		public SprintConfig config = new SprintConfig(14, java.time.DayOfWeek.MONDAY, true);

		@Override
		public SprintConfig find() {
			return config;
		}

		@Override
		public SprintConfig save(SprintConfig config) {
			this.config = config;
			return config;
		}

	}

}
