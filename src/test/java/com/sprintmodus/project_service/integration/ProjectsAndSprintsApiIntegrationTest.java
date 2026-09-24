package com.sprintmodus.project_service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.sprintmodus.project_service.integration.TenantFixtures.Tenant;
import com.sprintmodus.project_service.integration.TenantFixtures.User;

/**
 * Definition of Done of Phase 4, through the real security filter and a real MySQL: projects and sprints are created and
 * closed over REST, the subscription limit comes from the JWT, and sprint end dates follow the tenant's configured length.
 * Every test builds its own tenant database, so tests never see each other's data.
 */
@SpringBootTest(properties = { "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false" })
@AutoConfigureMockMvc
class ProjectsAndSprintsApiIntegrationTest {

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.tenant.host", TenantFixtures.MYSQL::getHost);
		registry.add("spring.datasource.tenant.port", () -> TenantFixtures.MYSQL.getMappedPort(3306));
		registry.add("spring.datasource.tenant.username", () -> "root");
		registry.add("spring.datasource.tenant.password", () -> TenantFixtures.ROOT_PASSWORD);
		registry.add("spring.datasource.tenant.jdbc-parameters", () -> TenantFixtures.PARAMS);
		registry.add("sprintmodus.jwt.secret", () -> TenantFixtures.SECRET);
	}

	@Autowired
	MockMvc mvc;

	/** A caller: a tenant, one of its users, and the project limit of their subscription. */
	private record Caller(Tenant tenant, User user, int maxProjects) {

		String token() {
			return TenantFixtures.token(tenant, user, maxProjects);
		}

	}

	private static Caller owner(Tenant tenant, int maxProjects) {
		return new Caller(tenant, tenant.owner(), maxProjects);
	}

	private static Caller member(Tenant tenant, int maxProjects) {
		return new Caller(tenant, tenant.member(), maxProjects);
	}

	private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, Caller caller, String body)
			throws Exception {
		request.header("Authorization", "Bearer " + caller.token());
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mvc.perform(request);
	}

	private ResultActions createProject(Caller caller, String name) throws Exception {
		return send(post("/api/projects"), caller, "{\"name\":\"%s\"}".formatted(name));
	}

	private String read(ResultActions result, String path) throws Exception {
		Object value = JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
		return String.valueOf(value);
	}

	private UUID newProject(Caller caller, String name) throws Exception {
		return UUID.fromString(read(createProject(caller, name).andExpect(status().isCreated()), "$.projectCode"));
	}

	private ResultActions createSprint(Caller caller, UUID project, String name, String start) throws Exception {
		String startJson = start == null ? "" : ",\"startDate\":\"%s\"".formatted(start);
		return send(post("/api/sprints"), caller, "{\"projectCode\":\"%s\",\"name\":\"%s\"%s}".formatted(project, name, startJson));
	}

	private UUID newSprint(Caller caller, UUID project, String name, String start) throws Exception {
		return UUID.fromString(read(createSprint(caller, project, name, start).andExpect(status().isCreated()), "$.sprintCode"));
	}

	// ---------------------------------------------------------------- projects

	@Test
	void createsAProjectWithADerivedKeyItsSequenceAndItsCreatorAsMember() throws Exception {
		Tenant tenant = TenantFixtures.newTenant();
		Caller owner = owner(tenant, 5);

		ResultActions created = createProject(owner, "Web App Rewrite").andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Web App Rewrite")).andExpect(jsonPath("$.key").value("WAR"))
				.andExpect(jsonPath("$.createdBy").value(tenant.owner().code().toString()))
				.andExpect(jsonPath("$.createdAt").exists()).andExpect(jsonPath("$.id").doesNotExist())
				.andExpect(jsonPath("$.projectId").doesNotExist());
		String code = read(created, "$.projectCode");

		assertThat(UUID.fromString(code).version()).as("an opaque v4 UUID").isEqualTo(4);
		assertThat(tenant.string("SELECT NextNumber FROM WorkItemSequence")).as("work items are numbered from 1000").isEqualTo("1000");
		assertThat(tenant.strings("SELECT BIN_TO_UUID(u.UserCode) FROM ProjectMember pm JOIN `User` u ON u.UserId = pm.UserId"))
				.containsExactly(tenant.owner().code().toString());

		send(get("/api/projects/" + code), owner, null).andExpect(status().isOk()).andExpect(jsonPath("$.key").value("WAR"));
		send(get("/api/projects"), owner, null).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].projectCode").value(code));
		send(get("/api/projects/" + code + "/members"), owner, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].fullName").value("Olivia Owner"))
				.andExpect(jsonPath("$[0].role").value("OWNER"));
	}

	@Test
	void theProjectLimitComesFromTheJwtAndIsEnforced() throws Exception {
		Tenant tenant = TenantFixtures.newTenant();
		Caller free = owner(tenant, 2);

		createProject(free, "One").andExpect(status().isCreated());
		createProject(free, "Two").andExpect(status().isCreated());
		createProject(free, "Three").andExpect(status().isPaymentRequired()).andExpect(jsonPath("$.code").value("PROJECT_LIMIT_REACHED"))
				.andExpect(jsonPath("$.message").value("Your plan allows at most 2 projects. Upgrade your plan to create more."));
		assertThat(tenant.string("SELECT COUNT(*) FROM Project")).isEqualTo("2");

		// the limit is whatever the caller's token says: a token issued after an upgrade lets the third one through
		createProject(owner(tenant, 3), "Three").andExpect(status().isCreated());
		createProject(owner(tenant, 3), "Four").andExpect(status().isPaymentRequired());
	}

	@Test
	void aDeletedProjectFreesItsSlot() throws Exception {
		Tenant tenant = TenantFixtures.newTenant();
		Caller owner = owner(tenant, 1);
		UUID first = newProject(owner, "Alpha");
		createProject(owner, "Beta").andExpect(status().isPaymentRequired());

		send(delete("/api/projects/" + first), owner, null).andExpect(status().isNoContent());

		createProject(owner, "Beta").andExpect(status().isCreated());
	}

	@Test
	void concurrentCreationsNeverExceedTheLimit() throws Exception {
		Tenant tenant = TenantFixtures.newTenant();
		Caller owner = owner(tenant, 3);
		CountDownLatch start = new CountDownLatch(1);

		List<Integer> statuses = new ArrayList<>();
		try (ExecutorService executor = Executors.newFixedThreadPool(12)) {
			List<Future<Integer>> results = new ArrayList<>();
			for (int i = 0; i < 12; i++) {
				String name = "Concurrent project " + i;
				results.add(executor.submit(() -> {
					start.await();
					return createProject(owner, name).andReturn().getResponse().getStatus();
				}));
			}
			start.countDown();
			for (Future<Integer> result : results) {
				statuses.add(result.get());
			}
		}

		assertThat(statuses).filteredOn(code -> code == 201).hasSize(3);
		assertThat(statuses).filteredOn(code -> code != 201).allMatch(code -> code == 402);
		assertThat(tenant.string("SELECT COUNT(*) FROM Project")).isEqualTo("3");
		assertThat(tenant.strings("SELECT `Key` FROM Project")).doesNotHaveDuplicates();
		assertThat(tenant.string("SELECT COUNT(*) FROM WorkItemSequence")).isEqualTo("3");
	}

	@Test
	void concurrentProjectsWithTheSameNameGetDistinctKeys() throws Exception {
		Tenant tenant = TenantFixtures.newTenant();
		Caller owner = owner(tenant, 20);
		CountDownLatch start = new CountDownLatch(1);

		try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
			List<Future<Integer>> results = new ArrayList<>();
			for (int i = 0; i < 8; i++) {
				results.add(executor.submit(() -> {
					start.await();
					return createProject(owner, "Web App Rewrite").andReturn().getResponse().getStatus();
				}));
			}
			start.countDown();
			for (Future<Integer> result : results) {
				assertThat(result.get()).isEqualTo(201);
			}
		}

		List<String> keys = tenant.strings("SELECT `Key` FROM Project ORDER BY ProjectId");
		assertThat(keys).hasSize(8).doesNotHaveDuplicates().contains("WAR", "WAR2");
	}

	@Test
	void keysAreUniqueUppercaseAndStayReservedAfterADelete() throws Exception {
		Tenant tenant = TenantFixtures.newTenant();
		Caller owner = owner(tenant, 10);

		send(post("/api/projects"), owner, "{\"name\":\"Chosen\",\"key\":\" web \"}").andExpect(status().isCreated())
				.andExpect(jsonPath("$.key").value("WEB"));
		send(post("/api/projects"), owner, "{\"name\":\"Other\",\"key\":\"WEB\"}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PROJECT_KEY_TAKEN"));
		send(post("/api/projects"), owner, "{\"name\":\"Other\",\"key\":\"1-x\"}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PROJECT_DATA"));
		createProject(owner, "Web App Rewrite").andExpect(jsonPath("$.key").value("WAR"));
		createProject(owner, "Warehouse Automation Robots").andExpect(jsonPath("$.key").value("WAR2"));

		UUID doomed = newProject(owner, "Zeta Team");
		send(delete("/api/projects/" + doomed), owner, null).andExpect(status().isNoContent());
		send(post("/api/projects"), owner, "{\"name\":\"Again\",\"key\":\"ZT\"}").andExpect(status().isConflict());
	}

	@Test
	void updatesTheNameAndDescriptionButNeverTheKey() throws Exception {
		Tenant tenant = TenantFixtures.newTenant();
		Caller member = member(tenant, 5);
		UUID project = UUID.fromString(read(send(post("/api/projects"), member, "{\"name\":\"Old\",\"key\":\"KEEP\"}").andExpect(status().isCreated()), "$.projectCode"));

		send(put("/api/projects/" + project), member, "{\"name\":\" New name \",\"description\":\"About it\",\"key\":\"CHANGED\"}")
				.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("New name")).andExpect(jsonPath("$.description").value("About it"))
				.andExpect(jsonPath("$.key").value("KEEP"));
		send(get("/api/projects/" + project), member, null).andExpect(jsonPath("$.name").value("New name")).andExpect(jsonPath("$.key").value("KEEP"));
		send(put("/api/projects/" + project), member, "{\"name\":\"  \"}").andExpect(status().isBadRequest());
		send(put("/api/projects/" + UUID.randomUUID()), member, "{\"name\":\"X\"}").andExpect(status().isNotFound());
	}

	@Test
	void onlyOwnersAndAdminsDeleteProjectsAndTheirSprintsGoWithThem() throws Exception {
		Tenant tenant = TenantFixtures.newTenant();
		Caller owner = owner(tenant, 5);
		Caller member = member(tenant, 5);
		UUID project = newProject(member, "Doomed");
		UUID sprint = newSprint(member, project, "S1", "2026-10-05");

		send(delete("/api/projects/" + project), member, null).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
		send(get("/api/projects/" + project), member, null).andExpect(status().isOk());

		send(delete("/api/projects/" + project), owner, null).andExpect(status().isNoContent());
		send(get("/api/projects/" + project), owner, null).andExpect(status().isNotFound());
		send(get("/api/sprints/" + sprint), owner, null).andExpect(status().isNotFound());
		send(get("/api/sprints?projectCode=" + project), owner, null).andExpect(status().isNotFound());
		send(delete("/api/projects/" + project), owner, null).andExpect(status().isNotFound());
		assertThat(tenant.string("SELECT COUNT(*) FROM Project WHERE IsActive = FALSE AND DeletedAt IS NOT NULL")).isEqualTo("1");
		assertThat(tenant.string("SELECT COUNT(*) FROM Sprint WHERE IsActive = FALSE AND DeletedAt IS NOT NULL")).isEqualTo("1");
		send(get("/api/projects"), owner, null).andExpect(jsonPath("$.length()").value(0));
	}

	// ---------------------------------------------------------------- tenant isolation

	@Test
	void tenantsNeverSeeEachOthersProjectsOrSprints() throws Exception {
		Tenant alpha = TenantFixtures.newTenant();
		Tenant beta = TenantFixtures.newTenant();
		Caller inAlpha = owner(alpha, 5);
		Caller inBeta = owner(beta, 5);
		UUID alphaProject = newProject(inAlpha, "Web App Rewrite");
		UUID alphaSprint = newSprint(inAlpha, alphaProject, "S1", "2026-10-05");

		send(get("/api/projects/" + alphaProject), inBeta, null).andExpect(status().isNotFound());
		send(get("/api/projects"), inBeta, null).andExpect(jsonPath("$.length()").value(0));
		send(get("/api/sprints/" + alphaSprint), inBeta, null).andExpect(status().isNotFound());
		send(get("/api/sprints?projectCode=" + alphaProject), inBeta, null).andExpect(status().isNotFound());
		createSprint(inBeta, alphaProject, "Sneaky", "2026-10-05").andExpect(status().isNotFound());
		send(delete("/api/projects/" + alphaProject), inBeta, null).andExpect(status().isNotFound());
		send(put("/api/sprints/" + alphaSprint + "/velocity"), inBeta, "{\"velocity\":99}").andExpect(status().isNotFound());

		// the same key is free in another tenant, and each tenant's data stays in its own database
		createProject(inBeta, "Web App Rewrite").andExpect(status().isCreated()).andExpect(jsonPath("$.key").value("WAR"));
		assertThat(alpha.string("SELECT COUNT(*) FROM Project")).isEqualTo("1");
		assertThat(beta.string("SELECT COUNT(*) FROM Project")).isEqualTo("1");
		send(get("/api/projects/" + alphaProject), inAlpha, null).andExpect(status().isOk());
	}

	// ---------------------------------------------------------------- sprints

	@Test
	void theSprintEndDateFollowsTheTenantsConfiguredLength() throws Exception {
		Tenant tenant = TenantFixtures.newTenant();
		Caller owner = owner(tenant, 5);
		UUID project = newProject(owner, "Web App Rewrite");

		createSprint(owner, project, "Sprint 1", "2026-10-05").andExpect(status().isCreated())
				.andExpect(jsonPath("$.startDate").value("2026-10-05")).andExpect(jsonPath("$.endDate").value("2026-10-19"))
				.andExpect(jsonPath("$.configuredDays").value(14)).andExpect(jsonPath("$.status").value("PLANNED"))
				.andExpect(jsonPath("$.velocity").value(0)).andExpect(jsonPath("$.projectCode").value(project.toString()))
				.andExpect(jsonPath("$.sprintId").doesNotExist());

		send(put("/api/sprints/config"), owner, "{\"defaultSprintDays\":7,\"sprintStartDay\":\"WEDNESDAY\",\"velocityTrackingEnabled\":true}")
				.andExpect(status().isOk()).andExpect(jsonPath("$.defaultSprintDays").value(7));
		send(get("/api/sprints/config"), owner, null).andExpect(jsonPath("$.sprintStartDay").value("WEDNESDAY"));

		createSprint(owner, project, "Sprint 2", "2026-10-19").andExpect(status().isCreated()).andExpect(jsonPath("$.endDate").value("2026-10-26"))
				.andExpect(jsonPath("$.configuredDays").value(7));
		send(get("/api/sprints?projectCode=" + project), owner, null).andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].name").value("Sprint 1")).andExpect(jsonPath("$[0].configuredDays").value(14))
				.andExpect(jsonPath("$[1].name").value("Sprint 2"));
	}

	@Test
	void withoutAStartDateASprintStartsOnTheConfiguredWeekday() throws Exception {
		Tenant tenant = TenantFixtures.newTenant();
		Caller owner = owner(tenant, 5);
		UUID project = newProject(owner, "Web App Rewrite");
		LocalDate today = LocalDate.now(ZoneOffset.UTC);

		ResultActions created = createSprint(owner, project, "Auto", null).andExpect(status().isCreated());

		LocalDate start = LocalDate.parse(read(created, "$.startDate"));
		assertThat(start).isEqualTo(today.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)));
		assertThat(LocalDate.parse(read(created, "$.endDate"))).isEqualTo(start.plusDays(14));
	}

	@Test
	void sprintsOfAProjectNeverOverlapButMayBeBackToBack() throws Exception {
		Tenant tenant = TenantFixtures.newTenant();
		Caller owner = owner(tenant, 5);
		UUID project = newProject(owner, "Web App Rewrite");
		newSprint(owner, project, "S1", "2026-10-05");

		createSprint(owner, project, "Clash", "2026-10-18").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SPRINT_OVERLAP"));
		createSprint(owner, project, "Inside", "2026-10-10").andExpect(status().isConflict());
		createSprint(owner, project, "Runs into it", "2026-09-30").andExpect(status().isConflict());
		createSprint(owner, project, "Next", "2026-10-19").andExpect(status().isCreated());
		createSprint(owner, project, "Earlier", "2026-09-21").andExpect(status().isCreated());
		// another project's sprints are independent
		UUID other = newProject(owner, "Other Project");
		createSprint(owner, other, "Same dates", "2026-10-05").andExpect(status().isCreated());
		assertThat(tenant.string("SELECT COUNT(*) FROM Sprint")).isEqualTo("4");
	}

	@Test
	void concurrentSprintsForTheSameDatesYieldExactlyOne() throws Exception {
		Tenant tenant = TenantFixtures.newTenant();
		Caller owner = owner(tenant, 5);
		UUID project = newProject(owner, "Web App Rewrite");
		CountDownLatch start = new CountDownLatch(1);

		List<Integer> statuses = new ArrayList<>();
		try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
			List<Future<Integer>> results = new ArrayList<>();
			for (int i = 0; i < 8; i++) {
				String name = "Racing " + i;
				results.add(executor.submit(() -> {
					start.await();
					return createSprint(owner, project, name, "2026-10-05").andReturn().getResponse().getStatus();
				}));
			}
			start.countDown();
			for (Future<Integer> result : results) {
				statuses.add(result.get());
			}
		}

		assertThat(statuses).filteredOn(code -> code == 201).hasSize(1);
		assertThat(statuses).filteredOn(code -> code != 201).allMatch(code -> code == 409);
		assertThat(tenant.string("SELECT COUNT(*) FROM Sprint")).isEqualTo("1");
	}

	@Test
	void sprintValidationAndUnknownReferences() throws Exception {
		Tenant tenant = TenantFixtures.newTenant();
		Caller owner = owner(tenant, 5);
		UUID project = newProject(owner, "Web App Rewrite");

		createSprint(owner, UUID.randomUUID(), "S", "2026-10-05").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
		send(post("/api/sprints"), owner, "{\"projectCode\":\"%s\",\"name\":\"  \"}".formatted(project)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_SPRINT_DATA"));
		send(post("/api/sprints"), owner, "{\"projectCode\":\"%s\",\"name\":\"S\",\"plannedVelocity\":-1}".formatted(project)).andExpect(status().isBadRequest());
		send(post("/api/sprints"), owner, "{\"name\":\"S\"}").andExpect(status().isBadRequest());
		send(post("/api/sprints"), owner, "{\"projectCode\":\"%s\",\"name\":\"S\",\"startDate\":\"not-a-date\"}".formatted(project))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
		send(get("/api/sprints/" + UUID.randomUUID()), owner, null).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SPRINT_NOT_FOUND"));
		send(get("/api/sprints"), owner, null).andExpect(status().isBadRequest());
		assertThat(tenant.string("SELECT COUNT(*) FROM Sprint")).isEqualTo("0");
	}

	@Test
	void aSprintIsStartedThenClosedAndOnlyOneIsActivePerProject() throws Exception {
		Tenant tenant = TenantFixtures.newTenant();
		Caller owner = owner(tenant, 5);
		UUID project = newProject(owner, "Web App Rewrite");
		UUID first = newSprint(owner, project, "S1", "2026-10-05");
		UUID second = newSprint(owner, project, "S2", "2026-10-19");

		send(post("/api/sprints/" + first + "/close"), owner, null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_SPRINT_STATE"));
		send(post("/api/sprints/" + first + "/start"), owner, null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
		send(post("/api/sprints/" + first + "/start"), owner, null).andExpect(status().isConflict());
		send(post("/api/sprints/" + second + "/start"), owner, null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ANOTHER_SPRINT_ACTIVE"));

		send(post("/api/sprints/" + first + "/close"), owner, null).andExpect(status().isNoContent());
		send(get("/api/sprints/" + first), owner, null).andExpect(jsonPath("$.status").value("CLOSED"));
		send(post("/api/sprints/" + first + "/close"), owner, null).andExpect(status().isConflict());
		send(post("/api/sprints/" + second + "/start"), owner, null).andExpect(status().isOk());
		send(post("/api/sprints/" + UUID.randomUUID() + "/close"), owner, null).andExpect(status().isNotFound());
	}

	@Test
	void sprintsAreEditedOnlyWhileTheRulesAllow() throws Exception {
		Tenant tenant = TenantFixtures.newTenant();
		Caller owner = owner(tenant, 5);
		UUID project = newProject(owner, "Web App Rewrite");
		UUID first = newSprint(owner, project, "S1", "2026-10-05");
		newSprint(owner, project, "S2", "2026-10-19");

		send(put("/api/sprints/" + first), owner, "{\"name\":\"Renamed\",\"plannedVelocity\":30}").andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Renamed")).andExpect(jsonPath("$.plannedVelocity").value(30))
				.andExpect(jsonPath("$.startDate").value("2026-10-05")).andExpect(jsonPath("$.endDate").value("2026-10-19"));
		send(get("/api/sprints/" + first), owner, null).andExpect(jsonPath("$.name").value("Renamed"));
		send(put("/api/sprints/" + first), owner, "{\"name\":\"S1\",\"startDate\":\"2026-10-12\"}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SPRINT_OVERLAP"));
		send(put("/api/sprints/" + first), owner, "{\"name\":\"S1\",\"startDate\":\"2026-09-21\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.startDate").value("2026-09-21")).andExpect(jsonPath("$.endDate").value("2026-10-05"));

		send(post("/api/sprints/" + first + "/start"), owner, null).andExpect(status().isOk());
		send(put("/api/sprints/" + first), owner, "{\"name\":\"S1\",\"startDate\":\"2026-09-14\"}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVALID_SPRINT_STATE"));
		send(put("/api/sprints/" + first), owner, "{\"name\":\"Still renamable\"}").andExpect(status().isOk());
		send(post("/api/sprints/" + first + "/close"), owner, null).andExpect(status().isNoContent());
		send(put("/api/sprints/" + first), owner, "{\"name\":\"Too late\"}").andExpect(status().isConflict());
		send(get("/api/sprints/" + first), owner, null).andExpect(jsonPath("$.name").value("Still renamable"));
	}

	// ---------------------------------------------------------------- velocity

	/** Two statuses per item type (NEW, and DONE which is terminal), as the Phase 6 seed will provide. */
	private static void seedWorkflow(Tenant tenant) {
		for (String type : List.of("PBI", "BUG", "TASK")) {
			tenant.execute("INSERT INTO WorkItemStatus (StatusCode, DisplayName, ItemType, SortOrder, IsTerminal) VALUES "
					+ "('NEW', 'New', ?, 1, FALSE), ('DONE', 'Done', ?, 2, TRUE)", type, type);
		}
	}

	private static void seedItem(Tenant tenant, UUID project, UUID sprint, int number, String type, String status, int points) {
		tenant.execute("INSERT INTO WorkItem (WorkItemNumber, ProjectId, SprintId, Type, StatusId, Title, EffortPoints, CreatedBy) "
				+ "SELECT ?, p.ProjectId, s.SprintId, ?, st.StatusId, 'Item', ?, u.UserId "
				+ "FROM Project p, Sprint s, WorkItemStatus st, `User` u "
				+ "WHERE p.ProjectCode = UUID_TO_BIN(?) AND s.SprintCode = UUID_TO_BIN(?) AND st.ItemType = ? AND st.StatusCode = ? AND u.Role = 'OWNER'",
				number, type, points, project.toString(), sprint.toString(), type, status);
	}

	@Test
	void velocityIsRecordedByWorkitemServiceAndReportedNextToTheItemMetrics() throws Exception {
		Tenant tenant = TenantFixtures.newTenant();
		Caller owner = owner(tenant, 5);
		UUID project = newProject(owner, "Web App Rewrite");
		UUID sprint = newSprint(owner, project, "S1", "2026-10-05");
		seedWorkflow(tenant);
		seedItem(tenant, project, sprint, 1000, "PBI", "NEW", 5);
		seedItem(tenant, project, sprint, 1001, "PBI", "DONE", 3);
		seedItem(tenant, project, sprint, 1002, "BUG", "DONE", 2);
		seedItem(tenant, project, sprint, 1003, "TASK", "DONE", 8);

		// exactly what workitem-service's Feign client sends
		send(put("/api/sprints/" + sprint + "/velocity"), owner, "{\"velocity\":5}").andExpect(status().isNoContent());
		send(put("/api/sprints/" + sprint + "/velocity"), owner, "{\"velocity\":8}").andExpect(status().isNoContent());

		send(get("/api/sprints/" + sprint + "/velocity"), owner, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.sprintCode").value(sprint.toString())).andExpect(jsonPath("$.velocity").value(8))
				.andExpect(jsonPath("$.velocityTrackingEnabled").value(true))
				.andExpect(jsonPath("$.metrics.totalItems").value(4)).andExpect(jsonPath("$.metrics.completedItems").value(3))
				.andExpect(jsonPath("$.metrics.plannedEffort").value(10)).andExpect(jsonPath("$.metrics.completedEffort").value(5))
				.andExpect(jsonPath("$.metrics.defectCount").value(1));
		send(get("/api/sprints/" + sprint), owner, null).andExpect(jsonPath("$.velocity").value(8));
	}

	@Test
	void anEmptySprintHasZeroMetrics() throws Exception {
		Tenant tenant = TenantFixtures.newTenant();
		Caller owner = owner(tenant, 5);
		UUID sprint = newSprint(owner, newProject(owner, "Web App Rewrite"), "S1", "2026-10-05");

		send(get("/api/sprints/" + sprint + "/velocity"), owner, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.metrics.totalItems").value(0)).andExpect(jsonPath("$.metrics.plannedEffort").value(0))
				.andExpect(jsonPath("$.velocity").value(0));
	}

	@Test
	void theVelocityOfAClosedSprintIsFrozen() throws Exception {
		Tenant tenant = TenantFixtures.newTenant();
		Caller owner = owner(tenant, 5);
		UUID sprint = newSprint(owner, newProject(owner, "Web App Rewrite"), "S1", "2026-10-05");
		send(post("/api/sprints/" + sprint + "/start"), owner, null).andExpect(status().isOk());
		send(put("/api/sprints/" + sprint + "/velocity"), owner, "{\"velocity\":13}").andExpect(status().isNoContent());
		send(post("/api/sprints/" + sprint + "/close"), owner, null).andExpect(status().isNoContent());

		send(put("/api/sprints/" + sprint + "/velocity"), owner, "{\"velocity\":99}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVALID_SPRINT_STATE"));
		send(get("/api/sprints/" + sprint + "/velocity"), owner, null).andExpect(jsonPath("$.velocity").value(13));
	}

	@Test
	void withVelocityTrackingOffTheUpdateSucceedsAndRecordsNothing() throws Exception {
		Tenant tenant = TenantFixtures.newTenant();
		Caller owner = owner(tenant, 5);
		UUID sprint = newSprint(owner, newProject(owner, "Web App Rewrite"), "S1", "2026-10-05");
		send(put("/api/sprints/config"), owner, "{\"defaultSprintDays\":14,\"sprintStartDay\":\"MONDAY\",\"velocityTrackingEnabled\":false}").andExpect(status().isOk());

		send(put("/api/sprints/" + sprint + "/velocity"), owner, "{\"velocity\":13}").andExpect(status().isNoContent());

		send(get("/api/sprints/" + sprint + "/velocity"), owner, null).andExpect(jsonPath("$.velocity").value(0))
				.andExpect(jsonPath("$.velocityTrackingEnabled").value(false));
	}

	@Test
	void velocityInputIsValidated() throws Exception {
		Tenant tenant = TenantFixtures.newTenant();
		Caller owner = owner(tenant, 5);
		UUID sprint = newSprint(owner, newProject(owner, "Web App Rewrite"), "S1", "2026-10-05");

		send(put("/api/sprints/" + sprint + "/velocity"), owner, "{\"velocity\":-4}").andExpect(status().isBadRequest());
		send(put("/api/sprints/" + sprint + "/velocity"), owner, "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SPRINT_DATA"));
		send(put("/api/sprints/" + sprint + "/velocity"), owner, "{\"velocity\":\"lots\"}").andExpect(status().isBadRequest());
		send(put("/api/sprints/" + UUID.randomUUID() + "/velocity"), owner, "{\"velocity\":1}").andExpect(status().isNotFound());
	}

	// ---------------------------------------------------------------- configuration, roles and the contract with workitem-service

	@Test
	void onlyOwnersAndAdminsChangeTheSprintConfiguration() throws Exception {
		Tenant tenant = TenantFixtures.newTenant();
		Caller member = member(tenant, 5);
		Caller owner = owner(tenant, 5);

		send(put("/api/sprints/config"), member, "{\"defaultSprintDays\":7,\"sprintStartDay\":\"MONDAY\",\"velocityTrackingEnabled\":true}")
				.andExpect(status().isForbidden());
		send(get("/api/sprints/config"), member, null).andExpect(status().isOk()).andExpect(jsonPath("$.defaultSprintDays").value(14))
				.andExpect(jsonPath("$.sprintStartDay").value("MONDAY")).andExpect(jsonPath("$.velocityTrackingEnabled").value(true));

		send(put("/api/sprints/config"), owner, "{\"defaultSprintDays\":0,\"sprintStartDay\":\"MONDAY\"}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_SPRINT_CONFIG"));
		send(put("/api/sprints/config"), owner, "{\"defaultSprintDays\":91,\"sprintStartDay\":\"MONDAY\"}").andExpect(status().isBadRequest());
		send(put("/api/sprints/config"), owner, "{\"defaultSprintDays\":14,\"sprintStartDay\":\"FUNDAY\"}").andExpect(status().isBadRequest());
		send(put("/api/sprints/config"), owner, "{\"defaultSprintDays\":14}").andExpect(status().isBadRequest());
		send(get("/api/sprints/config"), member, null).andExpect(jsonPath("$.defaultSprintDays").value(14));
	}

	@Test
	void theJsonMatchesWhatWorkitemServicesFeignStubExpects() throws Exception {
		Tenant tenant = TenantFixtures.newTenant();
		Caller owner = owner(tenant, 5);
		UUID project = newProject(owner, "Web App Rewrite");
		UUID sprint = newSprint(owner, project, "S1", "2026-10-05");

		// ProjectDto(projectCode, name, key) and SprintDto(sprintCode, projectCode, name, status, startDate, endDate, velocity)
		send(get("/api/projects/" + project), owner, null).andExpect(jsonPath("$.projectCode").value(project.toString()))
				.andExpect(jsonPath("$.name").value("Web App Rewrite")).andExpect(jsonPath("$.key").value("WAR"));
		send(get("/api/sprints/" + sprint), owner, null).andExpect(jsonPath("$.sprintCode").value(sprint.toString()))
				.andExpect(jsonPath("$.projectCode").value(project.toString())).andExpect(jsonPath("$.name").value("S1"))
				.andExpect(jsonPath("$.status").value("PLANNED")).andExpect(jsonPath("$.startDate").value("2026-10-05"))
				.andExpect(jsonPath("$.endDate").value("2026-10-19")).andExpect(jsonPath("$.velocity").value(0));
	}

	@Test
	void everyEndpointNeedsAValidTokenAndRejectsBadInputCleanly() throws Exception {
		Tenant tenant = TenantFixtures.newTenant();
		Caller owner = owner(tenant, 5);

		mvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/sprints").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
		mvc.perform(put("/api/sprints/config").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
		send(get("/api/projects/not-a-uuid"), owner, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
		send(get("/api/sprints/not-a-uuid"), owner, null).andExpect(status().isBadRequest());
		send(post("/api/projects"), owner, "{not json").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
		send(post("/api/projects"), owner, "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PROJECT_DATA"));
		send(get("/api/projects/" + UUID.randomUUID()), owner, null).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
	}

}
