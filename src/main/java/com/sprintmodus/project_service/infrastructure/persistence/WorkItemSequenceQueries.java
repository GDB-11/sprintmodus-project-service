package com.sprintmodus.project_service.infrastructure.persistence;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * The SQL project-service runs on {@code WorkItemSequence} (native queries only): it creates the counter of a new project,
 * whose {@code NextNumber} defaults to 1000. workitem-service hands the numbers out.
 */
interface WorkItemSequenceQueries extends Repository<WorkItemSequenceEntity, Long> {

	@Modifying(clearAutomatically = true)
	@Query(nativeQuery = true, value = "INSERT INTO WorkItemSequence (ProjectId) SELECT ProjectId FROM Project WHERE ProjectCode = UUID_TO_BIN(:projectCode)")
	int insertForProject(@Param("projectCode") String projectCode);

}
