package com.sprintmodus.project_service.infrastructure.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Every SQL statement on {@code TenantSprintConfig}, the tenant's single-row configuration (native queries only). */
interface TenantSprintConfigQueries extends Repository<TenantSprintConfigEntity, Integer> {

	@Query(nativeQuery = true, value = "SELECT ConfigId, DefaultSprintDays, SprintStartDay, VelocityTrackingEnabled FROM TenantSprintConfig WHERE ConfigId = 1")
	Optional<TenantSprintConfigEntity> find();

	/**
	 * Locks the configuration row until the transaction ends. The row always exists, so it serves as a per-tenant mutex:
	 * creating projects takes it, which makes the "count, then insert" of the plan limit safe under concurrency.
	 */
	@Query(nativeQuery = true, value = "SELECT ConfigId FROM TenantSprintConfig WHERE ConfigId = 1 FOR UPDATE")
	Optional<Integer> lockForUpdate();

	@Modifying(clearAutomatically = true)
	@Query(nativeQuery = true, value = """
			UPDATE TenantSprintConfig
			SET DefaultSprintDays = :days, SprintStartDay = :startDay, VelocityTrackingEnabled = :tracking
			WHERE ConfigId = 1
			""")
	int update(@Param("days") int days, @Param("startDay") String startDay, @Param("tracking") boolean tracking);

}
