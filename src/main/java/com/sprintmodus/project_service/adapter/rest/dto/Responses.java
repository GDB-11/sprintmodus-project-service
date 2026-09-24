package com.sprintmodus.project_service.adapter.rest.dto;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.sprintmodus.project_service.application.dto.Responses.MemberResponse;
import com.sprintmodus.project_service.application.dto.Responses.ProjectResponse;
import com.sprintmodus.project_service.application.dto.Responses.SprintConfigResponse;
import com.sprintmodus.project_service.application.dto.Responses.SprintResponse;
import com.sprintmodus.project_service.application.dto.Responses.VelocityResponse;
import com.sprintmodus.project_service.domain.model.OrganizationRole;
import com.sprintmodus.project_service.domain.model.SprintStatus;

/**
 * Bodies of the REST responses, named as clients (and workitem-service's Feign stub) expect: {@code projectCode},
 * {@code sprintCode}. Internal database ids are never part of them.
 */
public final class Responses {

	private Responses() {
	}

	public record Project(UUID projectCode, String name, String description, String key, UUID createdBy, Instant createdAt) {

		public static Project from(ProjectResponse response) {
			return new Project(response.code(), response.name(), response.description(), response.key(), response.createdBy(),
					response.createdAt());
		}

	}

	public record Member(UUID userCode, String fullName, String email, OrganizationRole role) {

		public static Member from(MemberResponse response) {
			return new Member(response.userCode(), response.fullName(), response.email(), response.role());
		}

	}

	public record Sprint(UUID sprintCode, UUID projectCode, String name, SprintStatus status, int configuredDays,
			LocalDate startDate, LocalDate endDate, int plannedVelocity, int velocity) {

		public static Sprint from(SprintResponse response) {
			return new Sprint(response.code(), response.projectCode(), response.name(), response.status(),
					response.configuredDays(), response.startDate(), response.endDate(), response.plannedVelocity(),
					response.velocity());
		}

	}

	public record Velocity(UUID sprintCode, SprintStatus status, int plannedVelocity, int velocity,
			boolean velocityTrackingEnabled, Metrics metrics) {

		public record Metrics(int totalItems, int completedItems, int plannedEffort, int completedEffort, int defectCount) {
		}

		public static Velocity from(VelocityResponse response) {
			var metrics = response.metrics();
			return new Velocity(response.sprintCode(), response.status(), response.plannedVelocity(), response.velocity(),
					response.velocityTrackingEnabled(), new Metrics(metrics.totalItems(), metrics.completedItems(),
							metrics.plannedEffort(), metrics.completedEffort(), metrics.defectCount()));
		}

	}

	public record SprintConfig(int defaultSprintDays, DayOfWeek sprintStartDay, boolean velocityTrackingEnabled) {

		public static SprintConfig from(SprintConfigResponse response) {
			return new SprintConfig(response.defaultSprintDays(), response.sprintStartDay(), response.velocityTrackingEnabled());
		}

	}

}
