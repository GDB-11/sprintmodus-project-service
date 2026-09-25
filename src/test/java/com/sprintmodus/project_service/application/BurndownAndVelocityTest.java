package com.sprintmodus.project_service.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sprintmodus.project_service.application.dto.Actor;
import com.sprintmodus.project_service.application.dto.Commands.CreateSprint;
import com.sprintmodus.project_service.application.dto.Commands.UpdateSprint;
import com.sprintmodus.project_service.application.error.SprintError;
import com.sprintmodus.project_service.application.port.persistence.BurndownRepository;
import com.sprintmodus.project_service.application.port.persistence.ProjectRepository.NewProject;
import com.sprintmodus.project_service.application.service.CloseSprintUseCase;
import com.sprintmodus.project_service.application.service.CreateSprintUseCase;
import com.sprintmodus.project_service.application.service.GetSprintBurndownUseCase;
import com.sprintmodus.project_service.application.service.GetVelocityHistoryUseCase;
import com.sprintmodus.project_service.application.service.StartSprintUseCase;
import com.sprintmodus.project_service.application.service.UpdateSprintUseCase;
import com.sprintmodus.project_service.domain.model.Burndown;
import com.sprintmodus.project_service.domain.model.Burndown.Point;
import com.sprintmodus.project_service.domain.model.Burndown.Snapshot;
import com.sprintmodus.project_service.domain.model.OrganizationRole;
import com.sprintmodus.project_service.domain.model.Sprint;
import com.sprintmodus.project_service.domain.model.SprintStatus;
import com.sprintmodus.project_service.domain.service.BurndownSeries;
import com.sprintmodus.project_service.support.Fakes;

/** The burndown series a chart draws, who may plan sprints, and the velocity of the last sprints. */
class BurndownAndVelocityTest {

	private static final LocalDate START = LocalDate.of(2026, 10, 5);

	private final Fakes.Projects projects = new Fakes.Projects();

	private final Fakes.Sprints sprints = new Fakes.Sprints();

	private final Fakes.Configs configs = new Fakes.Configs();

	private final Actor member = new Actor(UUID.randomUUID(), OrganizationRole.MEMBER);

	private final Actor owner = new Actor(UUID.randomUUID(), OrganizationRole.OWNER);

	private static BigDecimal h(String hours) {
		return new BigDecimal(hours);
	}

	private static Snapshot at(int day, String hours) {
		return new Snapshot(START.plusDays(day - 1), h(hours));
	}

	private static Sprint sprint(SprintStatus status, int days) {
		return new Sprint(UUID.randomUUID(), UUID.randomUUID(), "Sprint 1", status, days, START, START.plusDays(days), 0, 0);
	}

	private static List<String> remaining(Burndown burndown) {
		return burndown.points().stream().map(p -> p.remainingHours() == null ? "-" : p.remainingHours().toPlainString()).toList();
	}

	private static List<String> ideal(Burndown burndown) {
		return burndown.points().stream().map(p -> p.idealRemainingHours().toPlainString()).toList();
	}

	// ------------------------------------------------------------------ the series

	@Test
	void theIdealFallsLinearlyFromTheBaselineToNothingOnTheLastDay() {
		var burndown = BurndownSeries.build(sprint(SprintStatus.ACTIVE, 4), List.of(new Snapshot(START.minusDays(1), h("40"))), START);

		assertThat(ideal(burndown)).containsExactly("40.00", "30.00", "20.00", "10.00", "0.00");
		assertThat(burndown.baselineHours()).isEqualByComparingTo("40");
		assertThat(burndown.points()).extracting(Point::day).containsExactly(0, 1, 2, 3, 4);
		assertThat(burndown.points().getFirst().date()).as("day 0 is the eve of the first day").isEqualTo(START.minusDays(1));
		assertThat(burndown.points().get(1).date()).isEqualTo(START);
	}

	@Test
	void aDayWithoutASnapshotCarriesTheLastValueForward() {
		var snapshots = List.of(new Snapshot(START.minusDays(1), h("40")), at(1, "36"), at(3, "20"));

		var burndown = BurndownSeries.build(sprint(SprintStatus.ACTIVE, 5), snapshots, START.plusDays(3));

		assertThat(remaining(burndown)).as("nothing changed on day 2, so nothing burned; day 5 has not happened")
				.containsExactly("40", "36", "36", "20", "20", "-");
	}

	@Test
	void anActiveSprintHasNoValuesAfterToday() {
		var burndown = BurndownSeries.build(sprint(SprintStatus.ACTIVE, 4), List.of(new Snapshot(START.minusDays(1), h("10"))), START.plusDays(1));

		assertThat(remaining(burndown)).containsExactly("10", "10", "10", "-", "-");
	}

	@Test
	void aSprintNotYetStartedHasTheIdealLineOnly() {
		var burndown = BurndownSeries.build(sprint(SprintStatus.PLANNED, 3), List.of(), START.minusDays(10));

		assertThat(remaining(burndown)).containsExactly("-", "-", "-", "-");
		assertThat(ideal(burndown)).containsExactly("0.00", "0.00", "0.00", "0.00");
	}

	@Test
	void aClosedSprintEndsWhereItsLastSnapshotWas() {
		var snapshots = List.of(new Snapshot(START.minusDays(1), h("30")), at(1, "30"), at(2, "12"), at(3, "0"));

		var burndown = BurndownSeries.build(sprint(SprintStatus.CLOSED, 5), snapshots, START.plusDays(60));

		assertThat(remaining(burndown)).as("closed on day 3: the later days never happened").containsExactly("30", "30", "12", "0", "-", "-");
	}

	@Test
	void withoutADayZeroRowTheEarliestSnapshotIsTheBaseline() {
		var burndown = BurndownSeries.build(sprint(SprintStatus.ACTIVE, 4), List.of(at(2, "24"), at(3, "18")), START.plusDays(2));

		assertThat(burndown.baselineHours()).isEqualByComparingTo("24");
		assertThat(ideal(burndown)).containsExactly("24.00", "18.00", "12.00", "6.00", "0.00");
	}

	@Test
	void aSprintStartedLateKeepsItsBaselineAndFillsTheDaysBeforeTheStart() {
		// started on day 3: the baseline was recorded that day, dated the eve of day 1
		var snapshots = List.of(new Snapshot(START.minusDays(1), h("20")), at(3, "18"));

		var burndown = BurndownSeries.build(sprint(SprintStatus.ACTIVE, 4), snapshots, START.plusDays(2));

		assertThat(remaining(burndown)).containsExactly("20", "20", "20", "18", "-");
	}

	@Test
	void anEmptySprintIsAFlatLineAtZero() {
		var burndown = BurndownSeries.build(sprint(SprintStatus.ACTIVE, 3), List.of(), START);

		assertThat(remaining(burndown)).containsExactly("0", "0", "-", "-");
		assertThat(ideal(burndown)).containsOnly("0.00");
	}

	@Test
	void theBurndownUseCaseReadsTheRecordedSnapshotsOfTheSprintAtTheClocksToday() {
		UUID project = sprints.addProject();
		var stored = sprints.add(project, "S", SprintStatus.ACTIVE, START, 4);
		BurndownRepository recorded = _ -> List.of(new Snapshot(START.minusDays(1), h("8")), at(1, "6"));
		var clock = Clock.fixed(START.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

		var burndown = new GetSprintBurndownUseCase(sprints, recorded, clock).execute(stored.code());

		assertThat(remaining(burndown.getValue())).containsExactly("8", "6", "6", "-", "-");
		assertThat(new GetSprintBurndownUseCase(sprints, recorded, clock).execute(UUID.randomUUID()).getError())
				.isInstanceOf(SprintError.SprintNotFound.class);
	}

	// ------------------------------------------------------------------ who plans sprints

	@Test
	void onlyOwnersAndAdminsCreateChangeStartAndCloseSprints() {
		UUID project = sprints.addProject();
		var create = new CreateSprintUseCase(sprints, configs, Clock.fixed(Instant.parse("2026-09-23T12:00:00Z"), ZoneOffset.UTC));
		var stored = sprints.add(project, "S", SprintStatus.PLANNED, START, 14);

		assertThat(create.execute(new CreateSprint(member, project, "New", START.plusDays(30), null)).getError()).isInstanceOf(SprintError.NotAllowed.class);
		assertThat(new UpdateSprintUseCase(sprints).execute(new UpdateSprint(member, stored.code(), "Renamed", null, null)).getError())
				.isInstanceOf(SprintError.NotAllowed.class);
		assertThat(new StartSprintUseCase(sprints).execute(member, stored.code()).getError()).isInstanceOf(SprintError.NotAllowed.class);
		assertThat(new CloseSprintUseCase(sprints).execute(member, stored.code()).getError()).isInstanceOf(SprintError.NotAllowed.class);
		assertThat(sprints.stored.getFirst().status()).isEqualTo(SprintStatus.PLANNED);
		assertThat(sprints.stored).hasSize(1);

		assertThat(new StartSprintUseCase(sprints).execute(owner, stored.code()).isSuccess()).isTrue();
	}

	// ------------------------------------------------------------------ velocity history

	private UUID projectWithClosedSprints(int... velocities) {
		var project = projects.createWithinLimit(new NewProject("P", null, "PRJ", owner.userCode()), 10).getValue().code();
		sprints.knownProjects.add(project);
		LocalDate start = START;
		for (int i = 0; i < velocities.length; i++) {
			var s = sprints.add(project, "Sprint " + (i + 1), SprintStatus.CLOSED, start, 14);
			sprints.stored.set(sprints.stored.indexOf(s), new Sprint(s.code(), s.projectCode(), s.name(), s.status(), 14, s.startDate(), s.endDate(),
					velocities[i] + 5, velocities[i]));
			start = start.plusDays(14);
		}
		return project;
	}

	@Test
	void theHistoryIsTheLastNClosedSprintsOldestFirstWithTheirAverage() {
		var project = projectWithClosedSprints(10, 20, 30, 41);
		var history = new GetVelocityHistoryUseCase(projects, sprints);

		var result = history.execute(project, 3).getValue();

		assertThat(result.sprints()).extracting(s -> s.name()).containsExactly("Sprint 2", "Sprint 3", "Sprint 4");
		assertThat(result.sprints()).extracting(s -> s.velocity()).containsExactly(20, 30, 41);
		assertThat(result.averageVelocity()).isEqualByComparingTo("30.3");
	}

	@Test
	void onlyClosedSprintsCountBecauseTheirVelocityIsFinal() {
		var project = projectWithClosedSprints(10);
		sprints.add(project, "Running", SprintStatus.ACTIVE, START.plusDays(100), 14);
		sprints.add(project, "Next", SprintStatus.PLANNED, START.plusDays(200), 14);

		var result = new GetVelocityHistoryUseCase(projects, sprints).execute(project, null).getValue();

		assertThat(result.sprints()).extracting(s -> s.name()).containsExactly("Sprint 1");
	}

	@Test
	void aProjectWithoutClosedSprintsAveragesZero() {
		var project = projectWithClosedSprints();

		var result = new GetVelocityHistoryUseCase(projects, sprints).execute(project, null).getValue();

		assertThat(result.sprints()).isEmpty();
		assertThat(result.averageVelocity()).isEqualByComparingTo("0");
	}

	@Test
	void theLimitAndTheProjectAreChecked() {
		var project = projectWithClosedSprints(10);
		var history = new GetVelocityHistoryUseCase(projects, sprints);

		assertThat(history.execute(project, 0).getError()).isInstanceOf(SprintError.InvalidSprintData.class);
		assertThat(history.execute(project, GetVelocityHistoryUseCase.MAX_SPRINTS + 1).getError()).isInstanceOf(SprintError.InvalidSprintData.class);
		assertThat(history.execute(UUID.randomUUID(), 3).getError()).isInstanceOf(SprintError.ProjectNotFound.class);
		assertThat(history.execute(project, null).getValue().sprints()).hasSize(1);
	}

}
