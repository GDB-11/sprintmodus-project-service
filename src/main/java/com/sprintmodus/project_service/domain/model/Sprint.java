package com.sprintmodus.project_service.domain.model;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A time-boxed iteration of a project. The date range is half-open, {@code [startDate, endDate)}, and
 * {@code endDate = startDate + configuredDays}, so the next sprint may start on the day this one ends.
 *
 * @param configuredDays the tenant's sprint length at the moment the sprint was created
 * @param plannedVelocity effort points planned for the sprint
 * @param velocity effort points completed, pushed by workitem-service; frozen once the sprint is closed
 */
public record Sprint(UUID code, UUID projectCode, String name, SprintStatus status, int configuredDays,
		LocalDate startDate, LocalDate endDate, int plannedVelocity, int velocity) {
}
