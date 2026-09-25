package com.sprintmodus.project_service.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.sprintmodus.project_service.domain.model.Burndown;
import com.sprintmodus.project_service.domain.model.Burndown.Point;
import com.sprintmodus.project_service.domain.model.Burndown.Snapshot;
import com.sprintmodus.project_service.domain.model.Sprint;
import com.sprintmodus.project_service.domain.model.SprintStatus;

/**
 * Turns the snapshots that were recorded into the series a chart draws. A snapshot exists only for a day something changed,
 * so a day without one carries the last value forward: nothing changed, so nothing burned. Pure: {@code today} is given.
 * <ul>
 * <li>Day 0's date is the eve of the sprint's first day; its value is the baseline. Snapshots older than that are ignored.
 * If a sprint predates day 0 rows, the earliest snapshot stands in for it.</li>
 * <li>The ideal falls linearly from the baseline (day 0) to 0 on the last day: {@code baseline * (days - day) / days}.</li>
 * <li>A planned sprint has no actual values. An active one has them up to today, a closed one up to its last snapshot (the
 * one taken when it closed): a burndown does not continue after the sprint has.</li>
 * </ul>
 */
public final class BurndownSeries {

	private BurndownSeries() {
	}

	public static Burndown build(Sprint sprint, List<Snapshot> recorded, LocalDate today) {
		int days = sprint.configuredDays();
		LocalDate eve = sprint.startDate().minusDays(1);
		List<Snapshot> inWindow = recorded.stream().filter(snapshot -> !snapshot.date().isBefore(eve))
				.sorted(Comparator.comparing(Snapshot::date)).toList();
		List<Snapshot> snapshots = !inWindow.isEmpty() ? inWindow
				: recorded.stream().sorted(Comparator.comparing(Snapshot::date)).limit(1).toList();

		boolean started = sprint.status() != SprintStatus.PLANNED;
		BigDecimal baseline = snapshots.stream().filter(snapshot -> snapshot.date().equals(eve)).findFirst()
				.or(() -> snapshots.stream().findFirst()).map(Snapshot::remainingHours).orElse(BigDecimal.ZERO);
		LocalDate lastDay = switch (sprint.status()) {
			case PLANNED -> null;
			case ACTIVE -> today;
			case CLOSED -> snapshots.isEmpty() ? sprint.endDate().minusDays(1) : snapshots.getLast().date();
		};

		List<Point> points = new ArrayList<>();
		for (int day = 0; day <= days; day++) {
			LocalDate date = eve.plusDays(day);
			BigDecimal ideal = baseline.multiply(BigDecimal.valueOf(days - day)).divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
			BigDecimal remaining = null;
			if (started && (day == 0 || !date.isAfter(lastDay))) {
				// the last snapshot on or before this day; before the first one, the baseline
				remaining = snapshots.stream().filter(snapshot -> !snapshot.date().isAfter(date)).reduce((_, later) -> later)
						.map(Snapshot::remainingHours).orElse(baseline);
			}
			points.add(new Point(day, date, ideal, remaining));
		}
		return new Burndown(sprint.code(), sprint.name(), sprint.status(), sprint.startDate(), sprint.endDate(), days, baseline,
				List.copyOf(points));
	}

}
