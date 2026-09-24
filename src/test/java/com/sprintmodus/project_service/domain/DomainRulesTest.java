package com.sprintmodus.project_service.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.sprintmodus.project_service.domain.model.OrganizationRole;
import com.sprintmodus.project_service.domain.model.ProjectKey;
import com.sprintmodus.project_service.domain.model.SprintConfig;
import com.sprintmodus.project_service.domain.service.SprintSchedule;

class DomainRulesTest {

	@ParameterizedTest
	@ValueSource(strings = { "WAR", "war", "  war  ", "A1", "ABCDEFGHIJ", "P2P" })
	void acceptsWellFormedKeysAndUppercasesThem(String raw) {
		assertThat(ProjectKey.parse(raw).getValue()).isEqualTo(raw.trim().toUpperCase());
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "  ", "A", "1AB", "AB-C", "AB C", "ABCDEFGHIJK", "ÁB" })
	void rejectsMalformedKeys(String raw) {
		assertThat(ProjectKey.parse(raw).isFailure()).isTrue();
	}

	@Test
	void rejectsANullKey() {
		assertThat(ProjectKey.parse(null).isFailure()).isTrue();
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			Web App Rewrite | WAR
			Sprintmodus | SPRI
			web | WEB
			Ünïcode Projéct | UP
			Q3 2026 Launch | Q2L
			2026 | PRJ
			--- | PRJ
			A | AP
			one two three four five six seven | OTTFF
			""")
	void suggestsAKeyFromTheName(String name, String expected) {
		String key = ProjectKey.suggestFrom(name);

		assertThat(key).isEqualTo(expected);
		assertThat(ProjectKey.parse(key).isSuccess()).as(key).isTrue();
	}

	@Test
	void suggestedKeysAreAlwaysValid() {
		for (String name : new String[] { "", "   ", "!!!", "9 8 7", "x", "Ω", "a b", "Project 42" }) {
			assertThat(ProjectKey.parse(ProjectKey.suggestFrom(name)).isSuccess()).as(name).isTrue();
		}
	}

	@Test
	void appendsANumberToMakeAKeyUniqueWithinTenCharacters() {
		assertThat(ProjectKey.withSuffix("WAR", 1)).isEqualTo("WAR");
		assertThat(ProjectKey.withSuffix("WAR", 2)).isEqualTo("WAR2");
		assertThat(ProjectKey.withSuffix("WAR", 12)).isEqualTo("WAR12");
		assertThat(ProjectKey.withSuffix("ABCDEFGHIJ", 2)).isEqualTo("ABCDEFGHI2").hasSize(10);
		assertThat(ProjectKey.withSuffix("ABCDEFGHIJ", 15)).isEqualTo("ABCDEFGH15").hasSize(10);
	}

	@Test
	void aSprintConfigMustHaveASaneLengthAndAStartDay() {
		assertThat(SprintConfig.validated(14, DayOfWeek.MONDAY, true).getValue()).isEqualTo(new SprintConfig(14, DayOfWeek.MONDAY, true));
		assertThat(SprintConfig.validated(1, DayOfWeek.SUNDAY, false).isSuccess()).isTrue();
		assertThat(SprintConfig.validated(90, DayOfWeek.SUNDAY, null).getValue().velocityTrackingEnabled()).as("defaults to on").isTrue();
		for (Integer days : new Integer[] { null, 0, -3, 91 }) {
			assertThat(SprintConfig.validated(days, DayOfWeek.MONDAY, true).isFailure()).as(String.valueOf(days)).isTrue();
		}
		assertThat(SprintConfig.validated(14, null, true).isFailure()).isTrue();
	}

	@Test
	void theNextStartIsTheFirstConfiguredWeekdayFromTodayOn() {
		SprintConfig mondays = new SprintConfig(14, DayOfWeek.MONDAY, true);

		assertThat(mondays.nextStart(LocalDate.of(2026, 9, 23))).as("a Wednesday").isEqualTo(LocalDate.of(2026, 9, 28));
		assertThat(mondays.nextStart(LocalDate.of(2026, 9, 28))).as("already a Monday").isEqualTo(LocalDate.of(2026, 9, 28));
		assertThat(mondays.nextStart(LocalDate.of(2026, 9, 27))).as("a Sunday").isEqualTo(LocalDate.of(2026, 9, 28));
	}

	@Test
	void aSprintEndsAConfiguredNumberOfDaysAfterItStarts() {
		assertThat(SprintSchedule.endDate(LocalDate.of(2026, 1, 5), 14)).isEqualTo(LocalDate.of(2026, 1, 19));
		assertThat(SprintSchedule.endDate(LocalDate.of(2026, 1, 5), 1)).isEqualTo(LocalDate.of(2026, 1, 6));
		assertThat(SprintSchedule.endDate(LocalDate.of(2026, 12, 25), 14)).as("across a year").isEqualTo(LocalDate.of(2027, 1, 8));
	}

	@Test
	void sprintsAreHalfOpenSoBackToBackSprintsDoNotOverlap() {
		LocalDate jan5 = LocalDate.of(2026, 1, 5);
		LocalDate jan19 = LocalDate.of(2026, 1, 19);
		LocalDate feb2 = LocalDate.of(2026, 2, 2);

		assertThat(SprintSchedule.overlap(jan5, jan19, jan19, feb2)).as("next starts the day this ends").isFalse();
		assertThat(SprintSchedule.overlap(jan19, feb2, jan5, jan19)).isFalse();
		assertThat(SprintSchedule.overlap(jan5, jan19, jan19.minusDays(1), feb2)).as("shares one day").isTrue();
		assertThat(SprintSchedule.overlap(jan5, feb2, jan19, jan19.plusDays(1))).as("contained").isTrue();
		assertThat(SprintSchedule.overlap(jan5, jan19, jan5, jan19)).as("identical").isTrue();
		assertThat(SprintSchedule.overlap(jan5, jan19, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 15))).isFalse();
	}

	@Test
	void onlyOwnersAndAdminsAdminister() {
		assertThat(OrganizationRole.OWNER.canAdminister()).isTrue();
		assertThat(OrganizationRole.ADMIN.canAdminister()).isTrue();
		assertThat(OrganizationRole.MEMBER.canAdminister()).isFalse();
	}

	@Test
	void anUnknownRoleGetsTheLeastPrivilege() {
		assertThat(OrganizationRole.parse("OWNER")).isEqualTo(OrganizationRole.OWNER);
		assertThat(OrganizationRole.parse("SUPERUSER")).isEqualTo(OrganizationRole.MEMBER);
		assertThat(OrganizationRole.parse(null)).isEqualTo(OrganizationRole.MEMBER);
		assertThat(OrganizationRole.parse("owner")).as("case matters: tokens use the enum names").isEqualTo(OrganizationRole.MEMBER);
	}

}
