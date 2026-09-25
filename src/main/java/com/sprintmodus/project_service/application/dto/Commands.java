package com.sprintmodus.project_service.application.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;

/** Inputs of the use cases. Fields that a client may leave out are nullable. */
public final class Commands {

	private Commands() {
	}

	public record CreateProject(Actor actor, String name, String description, String key) {
	}

	public record UpdateProject(UUID projectCode, String name, String description) {
	}

	public record CreateSprint(Actor actor, UUID projectCode, String name, LocalDate startDate, Integer plannedVelocity) {
	}

	/** {@code startDate} and {@code plannedVelocity} left {@code null} keep their current value. */
	public record UpdateSprint(Actor actor, UUID sprintCode, String name, LocalDate startDate, Integer plannedVelocity) {
	}

	public record UpdateSprintConfig(Actor actor, Integer defaultSprintDays, DayOfWeek sprintStartDay,
			Boolean velocityTrackingEnabled) {
	}

}
