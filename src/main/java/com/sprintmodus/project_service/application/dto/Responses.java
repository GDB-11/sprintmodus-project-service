package com.sprintmodus.project_service.application.dto;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.sprintmodus.project_service.domain.model.OrganizationRole;
import com.sprintmodus.project_service.domain.model.Project;
import com.sprintmodus.project_service.domain.model.ProjectMember;
import com.sprintmodus.project_service.domain.model.Sprint;
import com.sprintmodus.project_service.domain.model.SprintConfig;
import com.sprintmodus.project_service.domain.model.SprintMetrics;
import com.sprintmodus.project_service.domain.model.SprintStatus;

/** Outputs of the use cases. */
public final class Responses {

	private Responses() {
	}

	public record ProjectResponse(UUID code, String name, String description, String key, UUID createdBy, Instant createdAt) {

		public static ProjectResponse from(Project project) {
			return new ProjectResponse(project.code(), project.name(), project.description(), project.key(),
					project.createdBy(), project.createdAt());
		}

	}

	public record MemberResponse(UUID userCode, String fullName, String email, OrganizationRole role) {

		public static MemberResponse from(ProjectMember member) {
			return new MemberResponse(member.userCode(), member.fullName(), member.email(), member.role());
		}

	}

	public record SprintResponse(UUID code, UUID projectCode, String name, SprintStatus status, int configuredDays,
			LocalDate startDate, LocalDate endDate, int plannedVelocity, int velocity) {

		public static SprintResponse from(Sprint sprint) {
			return new SprintResponse(sprint.code(), sprint.projectCode(), sprint.name(), sprint.status(),
					sprint.configuredDays(), sprint.startDate(), sprint.endDate(), sprint.plannedVelocity(), sprint.velocity());
		}

	}

	/** The velocity recorded for a sprint next to what its work items currently add up to. */
	public record VelocityResponse(UUID sprintCode, SprintStatus status, int plannedVelocity, int velocity,
			boolean velocityTrackingEnabled, SprintMetrics metrics) {
	}

	public record SprintConfigResponse(int defaultSprintDays, DayOfWeek sprintStartDay, boolean velocityTrackingEnabled) {

		public static SprintConfigResponse from(SprintConfig config) {
			return new SprintConfigResponse(config.defaultSprintDays(), config.sprintStartDay(), config.velocityTrackingEnabled());
		}

	}

}
