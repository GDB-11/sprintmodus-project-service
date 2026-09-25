package com.sprintmodus.project_service.application.port.persistence;

import java.util.List;
import java.util.UUID;

import com.sprintmodus.project_service.domain.model.Burndown.Snapshot;

/** The burndown snapshots workitem-service records as the work happens (and this service, at the start and end of a sprint). */
public interface BurndownRepository {

	/** Every snapshot of a sprint, oldest first. */
	List<Snapshot> findBySprint(UUID sprintCode);

}
