package com.sprintmodus.project_service.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.sprintmodus.common_lib.tenant.TenantContext;
import com.sprintmodus.project_service.application.dto.Actor;
import com.sprintmodus.project_service.application.dto.Commands.CreateProject;
import com.sprintmodus.project_service.application.dto.Commands.UpdateProject;
import com.sprintmodus.project_service.application.error.ProjectError;
import com.sprintmodus.project_service.application.service.CreateProjectUseCase;
import com.sprintmodus.project_service.application.service.DeleteProjectUseCase;
import com.sprintmodus.project_service.application.service.GetProjectUseCase;
import com.sprintmodus.project_service.application.service.ListProjectMembersUseCase;
import com.sprintmodus.project_service.application.service.ListProjectsUseCase;
import com.sprintmodus.project_service.application.service.UpdateProjectUseCase;
import com.sprintmodus.project_service.domain.model.OrganizationRole;
import com.sprintmodus.project_service.support.Fakes;

class ProjectUseCasesTest {

	private final Fakes.Projects projects = new Fakes.Projects();

	private final Fakes.Members members = new Fakes.Members();

	private final Actor member = new Actor(UUID.randomUUID(), OrganizationRole.MEMBER);

	private final Actor admin = new Actor(UUID.randomUUID(), OrganizationRole.ADMIN);

	private final CreateProjectUseCase create = new CreateProjectUseCase(projects);

	@BeforeEach
	void plan() {
		// what the security filter sets from the JWT of a PRO plan
		TenantContext.set(UUID.randomUUID(), member.userCode(), 10, 50, 5000);
	}

	@AfterEach
	void clear() {
		TenantContext.clear();
	}

	private void limit(int maxProjects) {
		TenantContext.set(UUID.randomUUID(), member.userCode(), maxProjects, 5, 100);
	}

	private CreatedProject createOk(String name, String key) {
		return new CreatedProject(create.execute(new CreateProject(member, name, null, key)));
	}

	/** Tiny wrapper so tests read as "created.code()" without repeating Result plumbing. */
	private record CreatedProject(com.sprintmodus.common_lib.result.Result<com.sprintmodus.project_service.application.dto.Responses.ProjectResponse, ProjectError> result) {

		String key() {
			return result.getValue().key();
		}

	}

	// ------------------------------------------------------------------ create

	@Test
	void createsAProjectRecordingTheCreatorAndDerivingTheKey() {
		var result = create.execute(new CreateProject(member, "  Web App Rewrite ", " Rebuild the site ", null));

		var project = result.getValue();
		assertThat(project.name()).isEqualTo("Web App Rewrite");
		assertThat(project.description()).isEqualTo("Rebuild the site");
		assertThat(project.key()).isEqualTo("WAR");
		assertThat(project.createdBy()).isEqualTo(member.userCode());
		assertThat(projects.stored).hasSize(1);
	}

	@Test
	void takesTheLimitFromTheJwtClaimsInTheTenantContext() {
		limit(3);

		create.execute(new CreateProject(member, "First", null, null));

		assertThat(projects.maxSeen).isEqualTo(3);
	}

	@Test
	void refusesTheProjectThatWouldExceedThePlansLimit() {
		limit(2);
		createOk("One", null);
		createOk("Two", null);

		var third = create.execute(new CreateProject(member, "Three", null, null));

		assertThat(third.getError()).isEqualTo(new ProjectError.ProjectLimitReached(2));
		assertThat(third.getError().message()).isEqualTo("Your plan allows at most 2 projects. Upgrade your plan to create more.");
		assertThat(projects.stored).hasSize(2);
	}

	@Test
	void theLimitMessageIsSingularForOneProject() {
		limit(1);
		createOk("One", null);

		assertThat(create.execute(new CreateProject(member, "Two", null, null)).getError().message())
				.isEqualTo("Your plan allows at most 1 project. Upgrade your plan to create more.");
	}

	@Test
	void aDeletedProjectFreesItsSlotButNotItsKey() {
		limit(1);
		var first = create.execute(new CreateProject(member, "Alpha", null, "ALP")).getValue();
		new DeleteProjectUseCase(projects).execute(admin, first.code());

		assertThat(create.execute(new CreateProject(member, "Other", null, "ALP")).getError()).isInstanceOf(ProjectError.ProjectKeyTaken.class);
		assertThat(create.execute(new CreateProject(member, "Beta", null, "BET")).isSuccess()).isTrue();
	}

	@Test
	void usesAKeyTheCallerChoseAfterUppercasingIt() {
		assertThat(createOk("Anything", " web ").key()).isEqualTo("WEB");
	}

	@Test
	void aKeyTheCallerChoseThatIsTakenIsAConflictNotSilentlyChanged() {
		createOk("First", "WEB");

		var second = create.execute(new CreateProject(member, "Second", null, "web"));

		assertThat(second.getError()).isInstanceOf(ProjectError.ProjectKeyTaken.class);
		assertThat(projects.stored).hasSize(1);
	}

	@Test
	void makesADerivedKeyUniqueByAppendingANumber() {
		assertThat(createOk("Web App Rewrite", null).key()).isEqualTo("WAR");
		assertThat(createOk("Warehouse Automation Robots", null).key()).isEqualTo("WAR2");
		assertThat(createOk("We Are Ready", null).key()).isEqualTo("WAR3");
	}

	@Test
	void triesTheNextKeyWhenAnotherRequestTakesTheCandidateFirst() {
		projects.keyRaces = 2;

		assertThat(createOk("Web App Rewrite", null).key()).isEqualTo("WAR3");
	}

	@Test
	void givesUpAfterManyCollisions() {
		projects.keyRaces = 1_000;
		limit(999);

		assertThat(create.execute(new CreateProject(member, "Web App Rewrite", null, null)).getError())
				.isInstanceOf(ProjectError.ProjectKeyTaken.class);
	}

	@Test
	void doesNotLeakTheKeyOfAnInvalidChoiceButExplainsIt() {
		var result = create.execute(new CreateProject(member, "Ok", null, "1-bad"));

		assertThat(result.getError()).isInstanceOfSatisfying(ProjectError.InvalidProjectData.class, error -> {
			assertThat(error.field()).isEqualTo("key");
			assertThat(error.message()).contains("2 to 10");
		});
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "   ", "\t" })
	void rejectsAMissingName(String name) {
		assertThat(create.execute(new CreateProject(member, name, null, null)).getError())
				.isInstanceOfSatisfying(ProjectError.InvalidProjectData.class, error -> assertThat(error.field()).isEqualTo("name"));
	}

	@Test
	void rejectsTooLongNamesAndDescriptions() {
		assertThat(create.execute(new CreateProject(member, "x".repeat(256), null, null)).isFailure()).isTrue();
		assertThat(create.execute(new CreateProject(member, "x".repeat(255), null, null)).isSuccess()).isTrue();
		assertThat(create.execute(new CreateProject(member, "Ok", "d".repeat(4001), null)).getError())
				.isInstanceOfSatisfying(ProjectError.InvalidProjectData.class, error -> assertThat(error.field()).isEqualTo("description"));
	}

	@Test
	void treatsABlankDescriptionAsNone() {
		assertThat(create.execute(new CreateProject(member, "Ok", "   ", null)).getValue().description()).isNull();
	}

	@Test
	void failsLoudlyWhenTheRequestHasNoSubscriptionLimits() {
		TenantContext.clear();

		assertThatThrownBy(() -> create.execute(new CreateProject(member, "Ok", null, null))).isInstanceOf(IllegalStateException.class);
	}

	// ------------------------------------------------------------------ read, update, delete, members

	@Test
	void getsAndListsProjects() {
		var one = create.execute(new CreateProject(member, "One", null, null)).getValue();
		createOk("Two", null);

		assertThat(new GetProjectUseCase(projects).execute(one.code()).getValue().name()).isEqualTo("One");
		assertThat(new GetProjectUseCase(projects).execute(UUID.randomUUID()).getError()).isInstanceOf(ProjectError.ProjectNotFound.class);
		assertThat(new ListProjectsUseCase(projects).execute().getValue()).hasSize(2);
	}

	@Test
	void updatesNameAndDescriptionButNeverTheKey() {
		var project = create.execute(new CreateProject(member, "Old name", "old", "KEY")).getValue();

		var updated = new UpdateProjectUseCase(projects).execute(new UpdateProject(project.code(), " New name ", null)).getValue();

		assertThat(updated.name()).isEqualTo("New name");
		assertThat(updated.description()).isNull();
		assertThat(updated.key()).isEqualTo("KEY");
	}

	@Test
	void updateValidatesAndReportsAnUnknownProject() {
		var update = new UpdateProjectUseCase(projects);

		assertThat(update.execute(new UpdateProject(UUID.randomUUID(), "Name", null)).getError()).isInstanceOf(ProjectError.ProjectNotFound.class);
		assertThat(update.execute(new UpdateProject(UUID.randomUUID(), " ", null)).getError()).isInstanceOf(ProjectError.InvalidProjectData.class);
	}

	@Test
	void onlyOwnersAndAdminsMayDeleteAProject() {
		var project = create.execute(new CreateProject(member, "Doomed", null, null)).getValue();
		var delete = new DeleteProjectUseCase(projects);

		assertThat(delete.execute(member, project.code()).getError()).isInstanceOf(ProjectError.NotAllowed.class);
		assertThat(projects.stored).hasSize(1);

		assertThat(delete.execute(admin, project.code()).isSuccess()).isTrue();
		assertThat(projects.stored).isEmpty();
		assertThat(delete.execute(admin, project.code()).getError()).as("already gone").isInstanceOf(ProjectError.ProjectNotFound.class);
	}

	@Test
	void anUnauthorizedDeleteDoesNotRevealWhetherTheProjectExists() {
		var delete = new DeleteProjectUseCase(projects);

		assertThat(delete.execute(member, UUID.randomUUID()).getError()).isInstanceOf(ProjectError.NotAllowed.class);
	}

	@Test
	void listsTheMembersOfAKnownProjectOnly() {
		var project = create.execute(new CreateProject(member, "Team", null, null)).getValue();
		members.add("Ana Diaz", OrganizationRole.OWNER);
		members.add("Bob Ray", OrganizationRole.MEMBER);
		var list = new ListProjectMembersUseCase(projects, members);

		assertThat(list.execute(project.code()).getValue()).extracting(member -> member.fullName()).containsExactly("Ana Diaz", "Bob Ray");
		assertThat(list.execute(UUID.randomUUID()).getError()).isInstanceOf(ProjectError.ProjectNotFound.class);
	}

}
