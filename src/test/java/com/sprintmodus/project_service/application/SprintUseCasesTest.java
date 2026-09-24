package com.sprintmodus.project_service.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sprintmodus.project_service.application.dto.Actor;
import com.sprintmodus.project_service.application.dto.Commands.CreateSprint;
import com.sprintmodus.project_service.application.dto.Commands.UpdateSprint;
import com.sprintmodus.project_service.application.dto.Commands.UpdateSprintConfig;
import com.sprintmodus.project_service.application.error.SprintConfigError;
import com.sprintmodus.project_service.application.error.SprintError;
import com.sprintmodus.project_service.application.service.CloseSprintUseCase;
import com.sprintmodus.project_service.application.service.CreateSprintUseCase;
import com.sprintmodus.project_service.application.service.GetSprintConfigUseCase;
import com.sprintmodus.project_service.application.service.GetSprintUseCase;
import com.sprintmodus.project_service.application.service.GetSprintVelocityUseCase;
import com.sprintmodus.project_service.application.service.ListSprintsUseCase;
import com.sprintmodus.project_service.application.service.StartSprintUseCase;
import com.sprintmodus.project_service.application.service.UpdateSprintConfigUseCase;
import com.sprintmodus.project_service.application.service.UpdateSprintUseCase;
import com.sprintmodus.project_service.application.service.UpdateSprintVelocityUseCase;
import com.sprintmodus.project_service.domain.model.OrganizationRole;
import com.sprintmodus.project_service.domain.model.SprintConfig;
import com.sprintmodus.project_service.domain.model.SprintMetrics;
import com.sprintmodus.project_service.domain.model.SprintStatus;
import com.sprintmodus.project_service.support.Fakes;

class SprintUseCasesTest {

	/** Wednesday 23 September 2026. */
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-23T12:00:00Z"), ZoneOffset.UTC);

	private final Fakes.Projects projects = new Fakes.Projects();

	private final Fakes.Sprints sprints = new Fakes.Sprints();

	private final Fakes.Configs configs = new Fakes.Configs();

	private final Actor member = new Actor(UUID.randomUUID(), OrganizationRole.MEMBER);

	private final Actor admin = new Actor(UUID.randomUUID(), OrganizationRole.ADMIN);

	private final CreateSprintUseCase create = new CreateSprintUseCase(sprints, configs, CLOCK);

	private UUID project;

	@BeforeEach
	void aProject() {
		project = sprints.addProject();
	}

	private static LocalDate day(int month, int dayOfMonth) {
		return LocalDate.of(2026, month, dayOfMonth);
	}

	private com.sprintmodus.project_service.application.dto.Responses.SprintResponse created(LocalDate start) {
		return create.execute(new CreateSprint(member, project, "Sprint", start, null)).getValue();
	}

	// ------------------------------------------------------------------ create

	@Test
	void theEndDateIsTheStartPlusTheTenantsConfiguredLength() {
		var sprint = created(day(10, 5));

		assertThat(sprint.startDate()).isEqualTo(day(10, 5));
		assertThat(sprint.endDate()).isEqualTo(day(10, 19));
		assertThat(sprint.configuredDays()).isEqualTo(14);
		assertThat(sprint.status()).isEqualTo(SprintStatus.PLANNED);
		assertThat(sprint.velocity()).isZero();
	}

	@Test
	void aDifferentConfiguredLengthChangesTheEndDate() {
		configs.config = new SprintConfig(7, DayOfWeek.MONDAY, true);
		assertThat(created(day(10, 5)).endDate()).isEqualTo(day(10, 12));

		configs.config = new SprintConfig(21, DayOfWeek.MONDAY, true);
		var three = created(day(11, 2));
		assertThat(three.endDate()).isEqualTo(day(11, 23));
		assertThat(three.configuredDays()).isEqualTo(21);
	}

	@Test
	void withoutAStartDateItStartsOnTheNextConfiguredWeekday() {
		var sprint = create.execute(new CreateSprint(member, project, "Sprint", null, null)).getValue();

		assertThat(sprint.startDate()).as("the next Monday after Wednesday 23 Sep").isEqualTo(day(9, 28));
		assertThat(sprint.endDate()).isEqualTo(day(10, 12));
	}

	@Test
	void theConfiguredStartDayDrivesTheDefaultStart() {
		configs.config = new SprintConfig(14, DayOfWeek.FRIDAY, true);

		assertThat(create.execute(new CreateSprint(member, project, "S", null, null)).getValue().startDate()).isEqualTo(day(9, 25));
	}

	@Test
	void backToBackSprintsAreAllowedButOverlappingOnesAreNot() {
		created(day(10, 5));

		assertThat(create.execute(new CreateSprint(member, project, "Next", day(10, 19), null)).isSuccess()).as("starts the day the first ends").isTrue();
		assertThat(create.execute(new CreateSprint(member, project, "Clash", day(10, 18), null)).getError()).isInstanceOf(SprintError.SprintOverlap.class);
		assertThat(create.execute(new CreateSprint(member, project, "Inside", day(10, 10), null)).getError()).isInstanceOf(SprintError.SprintOverlap.class);
		assertThat(create.execute(new CreateSprint(member, project, "Before", day(9, 30), null)).getError()).as("would run into the first").isInstanceOf(SprintError.SprintOverlap.class);
		assertThat(create.execute(new CreateSprint(member, project, "Earlier", day(9, 21), null)).isSuccess()).as("ends the day the first starts").isTrue();
	}

	@Test
	void sprintsOfDifferentProjectsMayOverlap() {
		created(day(10, 5));
		UUID other = sprints.addProject();

		assertThat(create.execute(new CreateSprint(member, other, "Same dates", day(10, 5), null)).isSuccess()).isTrue();
	}

	@Test
	void rejectsAnUnknownProject() {
		assertThat(create.execute(new CreateSprint(member, UUID.randomUUID(), "S", day(10, 5), null)).getError())
				.isInstanceOf(SprintError.ProjectNotFound.class);
	}

	@Test
	void validatesTheInput() {
		assertThat(create.execute(new CreateSprint(member, null, "S", null, null)).getError()).isInstanceOf(SprintError.InvalidSprintData.class);
		assertThat(create.execute(new CreateSprint(member, project, "  ", null, null)).getError()).isInstanceOf(SprintError.InvalidSprintData.class);
		assertThat(create.execute(new CreateSprint(member, project, "x".repeat(256), null, null)).getError()).isInstanceOf(SprintError.InvalidSprintData.class);
		assertThat(create.execute(new CreateSprint(member, project, "S", null, -1)).getError()).isInstanceOf(SprintError.InvalidSprintData.class);
		assertThat(create.execute(new CreateSprint(member, project, "S", day(10, 5), 30)).getValue().plannedVelocity()).isEqualTo(30);
		assertThat(sprints.stored).hasSize(1);
	}

	// ------------------------------------------------------------------ update

	@Test
	void renamingAndReplanningKeepTheDates() {
		var sprint = created(day(10, 5));

		var updated = new UpdateSprintUseCase(sprints).execute(new UpdateSprint(sprint.code(), " Renamed ", null, 21)).getValue();

		assertThat(updated.name()).isEqualTo("Renamed");
		assertThat(updated.plannedVelocity()).isEqualTo(21);
		assertThat(updated.startDate()).isEqualTo(day(10, 5));
		assertThat(updated.endDate()).isEqualTo(day(10, 19));
	}

	@Test
	void movingASprintKeepsItsOwnLengthEvenAfterTheTenantChangedTheDefault() {
		var sprint = created(day(10, 5));
		configs.config = new SprintConfig(7, DayOfWeek.MONDAY, true);

		var moved = new UpdateSprintUseCase(sprints).execute(new UpdateSprint(sprint.code(), "S", day(10, 12), null)).getValue();

		assertThat(moved.startDate()).isEqualTo(day(10, 12));
		assertThat(moved.endDate()).as("still 14 days").isEqualTo(day(10, 26));
	}

	@Test
	void movingASprintOntoAnotherIsRejectedButOntoItselfIsFine() {
		var first = created(day(10, 5));
		created(day(10, 19));
		var update = new UpdateSprintUseCase(sprints);

		assertThat(update.execute(new UpdateSprint(first.code(), "S", day(10, 10), null)).getError()).isInstanceOf(SprintError.SprintOverlap.class);
		assertThat(update.execute(new UpdateSprint(first.code(), "S", day(10, 6), null)).getError()).as("would run into the second").isInstanceOf(SprintError.SprintOverlap.class);
		assertThat(update.execute(new UpdateSprint(first.code(), "S", day(10, 4), null)).isSuccess()).as("moving within its own old range").isTrue();
	}

	@Test
	void datesOnlyChangeBeforeTheSprintStarts() {
		var sprint = created(day(10, 5));
		new StartSprintUseCase(sprints).execute(sprint.code());
		var update = new UpdateSprintUseCase(sprints);

		assertThat(update.execute(new UpdateSprint(sprint.code(), "S", day(10, 12), null)).getError()).isInstanceOf(SprintError.InvalidSprintState.class);
		assertThat(update.execute(new UpdateSprint(sprint.code(), "Renamed", day(10, 5), 40)).isSuccess()).as("same date is not a move").isTrue();
	}

	@Test
	void aClosedSprintCannotBeChanged() {
		var sprint = created(day(10, 5));
		new StartSprintUseCase(sprints).execute(sprint.code());
		new CloseSprintUseCase(sprints).execute(sprint.code());

		assertThat(new UpdateSprintUseCase(sprints).execute(new UpdateSprint(sprint.code(), "New name", null, null)).getError())
				.isInstanceOf(SprintError.InvalidSprintState.class);
	}

	@Test
	void updateValidatesAndReportsAnUnknownSprint() {
		var update = new UpdateSprintUseCase(sprints);
		var sprint = created(day(10, 5));

		assertThat(update.execute(new UpdateSprint(UUID.randomUUID(), "S", null, null)).getError()).isInstanceOf(SprintError.SprintNotFound.class);
		assertThat(update.execute(new UpdateSprint(sprint.code(), " ", null, null)).getError()).isInstanceOf(SprintError.InvalidSprintData.class);
		assertThat(update.execute(new UpdateSprint(sprint.code(), "S", null, -5)).getError()).isInstanceOf(SprintError.InvalidSprintData.class);
	}

	// ------------------------------------------------------------------ lifecycle

	@Test
	void aSprintGoesFromPlannedToActiveToClosed() {
		var sprint = created(day(10, 5));
		var start = new StartSprintUseCase(sprints);
		var close = new CloseSprintUseCase(sprints);

		assertThat(close.execute(sprint.code()).getError()).as("cannot close a planned sprint").isInstanceOf(SprintError.InvalidSprintState.class);
		assertThat(start.execute(sprint.code()).getValue().status()).isEqualTo(SprintStatus.ACTIVE);
		assertThat(start.execute(sprint.code()).getError()).as("already started").isInstanceOf(SprintError.InvalidSprintState.class);
		assertThat(close.execute(sprint.code()).isSuccess()).isTrue();
		assertThat(sprints.findByCode(sprint.code()).orElseThrow().status()).isEqualTo(SprintStatus.CLOSED);
		assertThat(close.execute(sprint.code()).getError()).as("already closed").isInstanceOf(SprintError.InvalidSprintState.class);
		assertThat(start.execute(sprint.code()).getError()).isInstanceOf(SprintError.InvalidSprintState.class);
	}

	@Test
	void aProjectHasAtMostOneActiveSprint() {
		var first = created(day(10, 5));
		var second = created(day(10, 19));
		var start = new StartSprintUseCase(sprints);
		start.execute(first.code());

		assertThat(start.execute(second.code()).getError()).isInstanceOf(SprintError.AnotherSprintActive.class);

		new CloseSprintUseCase(sprints).execute(first.code());
		assertThat(start.execute(second.code()).isSuccess()).as("once the first is closed").isTrue();
	}

	@Test
	void differentProjectsCanEachHaveAnActiveSprint() {
		var a = created(day(10, 5));
		UUID other = sprints.addProject();
		var b = create.execute(new CreateSprint(member, other, "B", day(10, 5), null)).getValue();
		var start = new StartSprintUseCase(sprints);

		assertThat(start.execute(a.code()).isSuccess()).isTrue();
		assertThat(start.execute(b.code()).isSuccess()).isTrue();
	}

	@Test
	void unknownSprintsAreNotFound() {
		UUID nobody = UUID.randomUUID();

		assertThat(new StartSprintUseCase(sprints).execute(nobody).getError()).isInstanceOf(SprintError.SprintNotFound.class);
		assertThat(new CloseSprintUseCase(sprints).execute(nobody).getError()).isInstanceOf(SprintError.SprintNotFound.class);
		assertThat(new GetSprintUseCase(sprints).execute(nobody).getError()).isInstanceOf(SprintError.SprintNotFound.class);
		assertThat(new GetSprintVelocityUseCase(sprints, configs).execute(nobody).getError()).isInstanceOf(SprintError.SprintNotFound.class);
		assertThat(new UpdateSprintVelocityUseCase(sprints, configs).execute(nobody, 5).getError()).isInstanceOf(SprintError.SprintNotFound.class);
	}

	@Test
	void listsTheSprintsOfAKnownProject() {
		created(day(10, 5));
		created(day(10, 19));
		var list = new ListSprintsUseCase(projectsKnowing(project), sprints);

		assertThat(list.execute(project).getValue()).hasSize(2);
		assertThat(list.execute(UUID.randomUUID()).getError()).isInstanceOf(SprintError.ProjectNotFound.class);
	}

	private Fakes.Projects projectsKnowing(UUID code) {
		Fakes.Projects known = new Fakes.Projects();
		known.stored.add(new com.sprintmodus.project_service.domain.model.Project(code, "P", null, "PRJ", UUID.randomUUID(), Instant.now()));
		return known;
	}

	// ------------------------------------------------------------------ velocity

	@Test
	void recordsTheVelocityWorkitemServiceReports() {
		var sprint = created(day(10, 5));
		new StartSprintUseCase(sprints).execute(sprint.code());
		var update = new UpdateSprintVelocityUseCase(sprints, configs);

		assertThat(update.execute(sprint.code(), 13).isSuccess()).isTrue();
		assertThat(update.execute(sprint.code(), 21).isSuccess()).isTrue();

		assertThat(sprints.findByCode(sprint.code()).orElseThrow().velocity()).isEqualTo(21);
	}

	@Test
	void theVelocityOfAClosedSprintIsFrozen() {
		var sprint = created(day(10, 5));
		new StartSprintUseCase(sprints).execute(sprint.code());
		new UpdateSprintVelocityUseCase(sprints, configs).execute(sprint.code(), 13);
		new CloseSprintUseCase(sprints).execute(sprint.code());

		assertThat(new UpdateSprintVelocityUseCase(sprints, configs).execute(sprint.code(), 99).getError())
				.isInstanceOf(SprintError.InvalidSprintState.class);
		assertThat(sprints.findByCode(sprint.code()).orElseThrow().velocity()).isEqualTo(13);
	}

	@Test
	void withVelocityTrackingOffTheCallSucceedsAndRecordsNothing() {
		configs.config = new SprintConfig(14, DayOfWeek.MONDAY, false);
		var sprint = created(day(10, 5));

		assertThat(new UpdateSprintVelocityUseCase(sprints, configs).execute(sprint.code(), 13).isSuccess()).isTrue();
		assertThat(sprints.findByCode(sprint.code()).orElseThrow().velocity()).isZero();
	}

	@Test
	void rejectsANegativeVelocity() {
		var sprint = created(day(10, 5));

		assertThat(new UpdateSprintVelocityUseCase(sprints, configs).execute(sprint.code(), -1).getError())
				.isInstanceOf(SprintError.InvalidSprintData.class);
	}

	@Test
	void reportsTheRecordedVelocityNextToTheItemMetrics() {
		var sprint = created(day(10, 5));
		new UpdateSprintVelocityUseCase(sprints, configs).execute(sprint.code(), 8);
		sprints.metrics = new SprintMetrics(4, 3, 10, 5, 1);

		var report = new GetSprintVelocityUseCase(sprints, configs).execute(sprint.code()).getValue();

		assertThat(report.sprintCode()).isEqualTo(sprint.code());
		assertThat(report.velocity()).isEqualTo(8);
		assertThat(report.plannedVelocity()).isZero();
		assertThat(report.velocityTrackingEnabled()).isTrue();
		assertThat(report.metrics()).isEqualTo(new SprintMetrics(4, 3, 10, 5, 1));
	}

	// ------------------------------------------------------------------ configuration

	@Test
	void anAdminChangesTheSprintConfigurationAndNewSprintsFollowIt() {
		var update = new UpdateSprintConfigUseCase(configs);

		var result = update.execute(new UpdateSprintConfig(admin, 10, DayOfWeek.TUESDAY, false));

		assertThat(result.getValue().defaultSprintDays()).isEqualTo(10);
		assertThat(new GetSprintConfigUseCase(configs).execute().getValue().sprintStartDay()).isEqualTo(DayOfWeek.TUESDAY);
		assertThat(created(day(10, 5)).endDate()).isEqualTo(day(10, 15));
	}

	@Test
	void aMemberCannotChangeTheSprintConfiguration() {
		var result = new UpdateSprintConfigUseCase(configs).execute(new UpdateSprintConfig(member, 10, DayOfWeek.TUESDAY, true));

		assertThat(result.getError()).isInstanceOf(SprintConfigError.NotAllowed.class);
		assertThat(configs.config.defaultSprintDays()).isEqualTo(14);
	}

	@Test
	void anInvalidConfigurationIsRejectedAndNothingChanges() {
		var update = new UpdateSprintConfigUseCase(configs);

		assertThat(update.execute(new UpdateSprintConfig(admin, 0, DayOfWeek.MONDAY, true)).getError()).isInstanceOf(SprintConfigError.InvalidSprintConfig.class);
		assertThat(update.execute(new UpdateSprintConfig(admin, 91, DayOfWeek.MONDAY, true)).getError()).isInstanceOf(SprintConfigError.InvalidSprintConfig.class);
		assertThat(update.execute(new UpdateSprintConfig(admin, 14, null, true)).getError()).isInstanceOf(SprintConfigError.InvalidSprintConfig.class);
		assertThat(configs.config).isEqualTo(new SprintConfig(14, DayOfWeek.MONDAY, true));
	}

}
