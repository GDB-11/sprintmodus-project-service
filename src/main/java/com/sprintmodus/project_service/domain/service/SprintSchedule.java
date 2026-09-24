package com.sprintmodus.project_service.domain.service;

import java.time.LocalDate;

/** Date rules for sprints. A sprint is the half-open range {@code [start, end)}. */
public final class SprintSchedule {

	private SprintSchedule() {
	}

	/** The day a sprint of {@code days} days starting on {@code start} ends. */
	public static LocalDate endDate(LocalDate start, int days) {
		return start.plusDays(days);
	}

	/** Whether two half-open ranges share a day. A sprint may start on the day the previous one ends. */
	public static boolean overlap(LocalDate startA, LocalDate endA, LocalDate startB, LocalDate endB) {
		return startA.isBefore(endB) && startB.isBefore(endA);
	}

}
