package com.sprintmodus.project_service.domain.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import com.sprintmodus.common_lib.result.Result;

/**
 * The tenant's sprint settings.
 *
 * @param defaultSprintDays length of a new sprint, in days
 * @param sprintStartDay the weekday sprints normally start on; used to pick a start date when none is given
 * @param velocityTrackingEnabled whether velocity reported by workitem-service is recorded
 */
public record SprintConfig(int defaultSprintDays, DayOfWeek sprintStartDay, boolean velocityTrackingEnabled) {

	public static final int MIN_DAYS = 1;

	public static final int MAX_DAYS = 90;

	/** A configuration with the values checked; the error is a message safe to show. */
	public static Result<SprintConfig, String> validated(Integer days, DayOfWeek startDay, Boolean tracking) {
		if (days == null || days < MIN_DAYS || days > MAX_DAYS) {
			return Result.failure("The sprint length must be between " + MIN_DAYS + " and " + MAX_DAYS + " days.");
		}
		if (startDay == null) {
			return Result.failure("Choose the day of the week sprints start on.");
		}
		return Result.success(new SprintConfig(days, startDay, tracking == null || tracking));
	}

	/** The first day, today or later, that falls on the configured start day. */
	public LocalDate nextStart(LocalDate today) {
		return today.with(TemporalAdjusters.nextOrSame(sprintStartDay));
	}

}
