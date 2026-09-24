package com.sprintmodus.project_service.adapter.rest.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;

/** Bodies of the REST requests. Optional fields may be left out of the JSON. */
public final class Requests {

	private Requests() {
	}

	/** {@code key} left out: it is derived from the name. */
	public record CreateProject(String name, String description, String key) {
	}

	public record UpdateProject(String name, String description) {
	}

	/** {@code startDate} left out: the next configured start day. */
	public record CreateSprint(UUID projectCode, String name, LocalDate startDate, Integer plannedVelocity) {
	}

	/** {@code startDate} and {@code plannedVelocity} left out keep their current value. */
	public record UpdateSprint(String name, LocalDate startDate, Integer plannedVelocity) {
	}

	public record Velocity(Integer velocity) {
	}

	public record SprintConfig(Integer defaultSprintDays, DayOfWeek sprintStartDay, Boolean velocityTrackingEnabled) {
	}

}
