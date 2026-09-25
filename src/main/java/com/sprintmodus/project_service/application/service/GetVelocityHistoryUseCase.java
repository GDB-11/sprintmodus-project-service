package com.sprintmodus.project_service.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sprintmodus.common_lib.result.Result;
import com.sprintmodus.project_service.application.dto.Responses.SprintResponse;
import com.sprintmodus.project_service.application.error.SprintError;
import com.sprintmodus.project_service.application.error.SprintError.InvalidSprintData;
import com.sprintmodus.project_service.application.error.SprintError.ProjectNotFound;
import com.sprintmodus.project_service.application.port.persistence.ProjectRepository;
import com.sprintmodus.project_service.application.port.persistence.SprintRepository;
import com.sprintmodus.project_service.domain.model.SprintStatus;

/**
 * The velocity of a project's last N closed sprints, oldest first, and their average: what a team can plan the next sprint
 * with. Only closed sprints count, because only their velocity is final.
 */
@Service
public class GetVelocityHistoryUseCase {

	public static final int DEFAULT_SPRINTS = 6;

	public static final int MAX_SPRINTS = 24;

	/** {@code averageVelocity} is over the sprints listed, to one decimal; 0 when there are none. */
	public record History(UUID projectCode, List<SprintResponse> sprints, BigDecimal averageVelocity) {
	}

	private final ProjectRepository projects;

	private final SprintRepository sprints;

	public GetVelocityHistoryUseCase(ProjectRepository projects, SprintRepository sprints) {
		this.projects = projects;
		this.sprints = sprints;
	}

	public Result<History, SprintError> execute(UUID projectCode, Integer limit) {
		int count = limit == null ? DEFAULT_SPRINTS : limit;
		if (count < 1 || count > MAX_SPRINTS) {
			return Result.failure(new InvalidSprintData("limit", "Ask for between 1 and " + MAX_SPRINTS + " sprints."));
		}
		if (projects.findByCode(projectCode).isEmpty()) {
			return Result.failure(new ProjectNotFound());
		}
		List<SprintResponse> closed = sprints.findByProject(projectCode).stream().filter(sprint -> sprint.status() == SprintStatus.CLOSED)
				.sorted(Comparator.comparing(sprint -> sprint.startDate())).map(SprintResponse::from).toList();
		List<SprintResponse> last = closed.subList(Math.max(0, closed.size() - count), closed.size());
		BigDecimal average = last.isEmpty() ? BigDecimal.ZERO.setScale(1)
				: BigDecimal.valueOf(last.stream().mapToInt(SprintResponse::velocity).sum()).divide(BigDecimal.valueOf(last.size()), 1,
						RoundingMode.HALF_UP);
		return Result.success(new History(projectCode, last, average));
	}

}
