package com.sprintmodus.project_service.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Reads of {@code BurndownData} (native queries only). */
interface BurndownQueries extends Repository<BurndownEntity, Long> {

	@Query(nativeQuery = true, value = """
			SELECT b.BurndownId, b.SnapshotDate, b.RemainingHours
			FROM BurndownData b JOIN Sprint s ON s.SprintId = b.SprintId
			WHERE s.SprintCode = UUID_TO_BIN(:sprintCode) AND s.IsActive = TRUE
			ORDER BY b.SnapshotDate
			""")
	List<BurndownEntity> findBySprint(@Param("sprintCode") String sprintCode);

}
