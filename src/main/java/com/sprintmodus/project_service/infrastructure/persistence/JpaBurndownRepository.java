package com.sprintmodus.project_service.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.sprintmodus.project_service.application.port.persistence.BurndownRepository;
import com.sprintmodus.project_service.domain.model.Burndown.Snapshot;

@Repository
class JpaBurndownRepository implements BurndownRepository {

	private final BurndownQueries burndown;

	JpaBurndownRepository(BurndownQueries burndown) {
		this.burndown = burndown;
	}

	@Override
	public List<Snapshot> findBySprint(UUID sprintCode) {
		return burndown.findBySprint(sprintCode.toString()).stream()
				.map(entity -> new Snapshot(entity.snapshotDate, entity.remainingHours)).toList();
	}

}
