package com.sprintmodus.project_service.infrastructure.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Reads the {@code view_Sprint_Overview} view: what a sprint's work items add up to (native queries only). */
interface SprintOverviewQueries extends Repository<SprintOverviewEntity, Long> {

	@Query(nativeQuery = true, value = """
			SELECT SprintId, TotalItems, CompletedItems, PlannedEffort, CompletedEffort, DefectCount
			FROM view_Sprint_Overview
			WHERE SprintCode = UUID_TO_BIN(:code)
			""")
	Optional<SprintOverviewEntity> findByCode(@Param("code") String code);

}
