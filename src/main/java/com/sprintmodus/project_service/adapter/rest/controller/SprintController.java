package com.sprintmodus.project_service.adapter.rest.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sprintmodus.common_lib.security.AuthenticatedUser;
import com.sprintmodus.project_service.adapter.rest.dto.Requests;
import com.sprintmodus.project_service.adapter.rest.dto.Responses;
import com.sprintmodus.project_service.application.dto.Commands.CreateSprint;
import com.sprintmodus.project_service.application.dto.Commands.UpdateSprint;
import com.sprintmodus.project_service.application.dto.Commands.UpdateSprintConfig;
import com.sprintmodus.project_service.application.error.SprintError.InvalidSprintData;
import com.sprintmodus.project_service.application.service.CloseSprintUseCase;
import com.sprintmodus.project_service.application.service.CreateSprintUseCase;
import com.sprintmodus.project_service.application.service.GetSprintBurndownUseCase;
import com.sprintmodus.project_service.application.service.GetSprintConfigUseCase;
import com.sprintmodus.project_service.application.service.GetSprintUseCase;
import com.sprintmodus.project_service.application.service.GetSprintVelocityUseCase;
import com.sprintmodus.project_service.application.service.GetVelocityHistoryUseCase;
import com.sprintmodus.project_service.application.service.ListSprintsUseCase;
import com.sprintmodus.project_service.application.service.StartSprintUseCase;
import com.sprintmodus.project_service.application.service.UpdateSprintConfigUseCase;
import com.sprintmodus.project_service.application.service.UpdateSprintUseCase;
import com.sprintmodus.project_service.application.service.UpdateSprintVelocityUseCase;

/**
 * Maps HTTP to the sprint use cases (and the tenant's sprint configuration, at {@code /api/sprints/config}) and their
 * {@code Result}s back to HTTP. It holds no business logic. Sprints are addressed by their UUID code.
 */
@RestController
@RequestMapping("/api/sprints")
class SprintController {

	private final CreateSprintUseCase createSprint;

	private final ListSprintsUseCase listSprints;

	private final GetSprintUseCase getSprint;

	private final UpdateSprintUseCase updateSprint;

	private final StartSprintUseCase startSprint;

	private final CloseSprintUseCase closeSprint;

	private final GetSprintVelocityUseCase getVelocity;

	private final UpdateSprintVelocityUseCase updateVelocity;

	private final GetSprintBurndownUseCase getBurndown;

	private final GetVelocityHistoryUseCase getVelocityHistory;

	private final GetSprintConfigUseCase getConfig;

	private final UpdateSprintConfigUseCase updateConfig;

	SprintController(CreateSprintUseCase createSprint, ListSprintsUseCase listSprints, GetSprintUseCase getSprint,
			UpdateSprintUseCase updateSprint, StartSprintUseCase startSprint, CloseSprintUseCase closeSprint,
			GetSprintVelocityUseCase getVelocity, UpdateSprintVelocityUseCase updateVelocity, GetSprintBurndownUseCase getBurndown,
			GetVelocityHistoryUseCase getVelocityHistory, GetSprintConfigUseCase getConfig, UpdateSprintConfigUseCase updateConfig) {
		this.createSprint = createSprint;
		this.listSprints = listSprints;
		this.getSprint = getSprint;
		this.updateSprint = updateSprint;
		this.startSprint = startSprint;
		this.closeSprint = closeSprint;
		this.getVelocity = getVelocity;
		this.updateVelocity = updateVelocity;
		this.getBurndown = getBurndown;
		this.getVelocityHistory = getVelocityHistory;
		this.getConfig = getConfig;
		this.updateConfig = updateConfig;
	}

	@GetMapping
	ResponseEntity<?> list(@RequestParam UUID projectCode) {
		return listSprints.execute(projectCode).fold(sprints -> ResponseEntity.ok(sprints.stream().map(Responses.Sprint::from).toList()),
				ErrorMapper::toResponse);
	}

	@PostMapping
	ResponseEntity<?> create(@AuthenticationPrincipal AuthenticatedUser user, @RequestBody Requests.CreateSprint request) {
		return createSprint.execute(new CreateSprint(Actors.from(user), request.projectCode(), request.name(), request.startDate(),
				request.plannedVelocity())).fold(sprint -> ResponseEntity.status(HttpStatus.CREATED).body(Responses.Sprint.from(sprint)),
						ErrorMapper::toResponse);
	}

	@GetMapping("/{sprintCode}")
	ResponseEntity<?> get(@PathVariable UUID sprintCode) {
		return getSprint.execute(sprintCode).fold(sprint -> ResponseEntity.ok(Responses.Sprint.from(sprint)), ErrorMapper::toResponse);
	}

	@PutMapping("/{sprintCode}")
	ResponseEntity<?> update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sprintCode,
			@RequestBody Requests.UpdateSprint request) {
		return updateSprint.execute(new UpdateSprint(Actors.from(user), sprintCode, request.name(), request.startDate(), request.plannedVelocity()))
				.fold(sprint -> ResponseEntity.ok(Responses.Sprint.from(sprint)), ErrorMapper::toResponse);
	}

	@PostMapping("/{sprintCode}/start")
	ResponseEntity<?> start(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sprintCode) {
		return startSprint.execute(Actors.from(user), sprintCode).fold(sprint -> ResponseEntity.ok(Responses.Sprint.from(sprint)), ErrorMapper::toResponse);
	}

	@PostMapping("/{sprintCode}/close")
	ResponseEntity<?> close(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID sprintCode) {
		return closeSprint.execute(Actors.from(user), sprintCode).fold(_ -> ResponseEntity.noContent().build(), ErrorMapper::toResponse);
	}

	@GetMapping("/{sprintCode}/velocity")
	ResponseEntity<?> velocity(@PathVariable UUID sprintCode) {
		return getVelocity.execute(sprintCode).fold(velocity -> ResponseEntity.ok(Responses.Velocity.from(velocity)),
				ErrorMapper::toResponse);
	}

	/** Called by workitem-service with the caller's own token, whenever the completed effort of a sprint changes. */
	@PutMapping("/{sprintCode}/velocity")
	ResponseEntity<?> updateVelocity(@PathVariable UUID sprintCode, @RequestBody Requests.Velocity request) {
		if (request.velocity() == null) {
			return ErrorMapper.toResponse(new InvalidSprintData("velocity", "The velocity is required."));
		}
		return updateVelocity.execute(sprintCode, request.velocity()).fold(_ -> ResponseEntity.noContent().build(),
				ErrorMapper::toResponse);
	}

	/** The hours still to do at the end of each day of the sprint, next to the ideal line; see {@code Responses.Burndown}. */
	@GetMapping("/{sprintCode}/burndown")
	ResponseEntity<?> burndown(@PathVariable UUID sprintCode) {
		return getBurndown.execute(sprintCode).fold(burndown -> ResponseEntity.ok(Responses.Burndown.from(burndown)), ErrorMapper::toResponse);
	}

	/** The velocity of a project's last {@code limit} closed sprints (default 6, at most 24), oldest first, and their average. */
	@GetMapping("/velocity-history")
	ResponseEntity<?> velocityHistory(@RequestParam UUID projectCode, @RequestParam(required = false) Integer limit) {
		return getVelocityHistory.execute(projectCode, limit).fold(history -> ResponseEntity.ok(Responses.VelocityHistory.from(history)),
				ErrorMapper::toResponse);
	}

	@GetMapping("/config")
	ResponseEntity<?> config() {
		return getConfig.execute().fold(config -> ResponseEntity.ok(Responses.SprintConfig.from(config)), ErrorMapper::toResponse);
	}

	@PutMapping("/config")
	ResponseEntity<?> updateConfig(@AuthenticationPrincipal AuthenticatedUser user, @RequestBody Requests.SprintConfig request) {
		return updateConfig.execute(new UpdateSprintConfig(Actors.from(user), request.defaultSprintDays(), request.sprintStartDay(),
				request.velocityTrackingEnabled())).fold(config -> ResponseEntity.ok(Responses.SprintConfig.from(config)),
						ErrorMapper::toResponse);
	}

}
