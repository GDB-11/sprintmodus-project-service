package com.sprintmodus.project_service.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The burndown of a sprint: the hours still to do at the end of each of its days, next to the ideal straight line from the
 * scope at the start to nothing at the end. Day 0 is the eve of the first day (the scope the sprint began with); days
 * {@code 1..days} are the sprint's own days.
 *
 * @param baselineHours the hours in the sprint when it began: where the ideal line starts
 */
public record Burndown(UUID sprintCode, String sprintName, SprintStatus status, LocalDate startDate, LocalDate endDate, int days,
		BigDecimal baselineHours, List<Point> points) {

	/** {@code remainingHours} is {@code null} for a day that has not happened (or, in a closed sprint, came after it closed). */
	public record Point(int day, LocalDate date, BigDecimal idealRemainingHours, BigDecimal remainingHours) {
	}

	/** What was recorded: the hours left at the end of {@code date}. */
	public record Snapshot(LocalDate date, BigDecimal remainingHours) {
	}

}
