package com.sprintmodus.project_service.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Every SQL statement on {@code Sprint} (native queries only). A sprint is a half-open range {@code [StartDate, EndDate)},
 * so two sprints overlap when {@code a.StartDate < b.EndDate AND b.StartDate < a.EndDate}. Sprints are addressed by their
 * UUID code and joined to their project's code; internal ids never leave this package. Writes must run in the caller's
 * transaction.
 */
interface SprintQueries extends Repository<SprintEntity, Long> {

	String SELECT_SPRINT = """
			SELECT s.SprintId, s.SprintCode, p.ProjectCode, s.Name, s.Status, s.ConfiguredDays, s.StartDate, s.EndDate,
			       s.PlannedVelocity, s.Velocity
			FROM Sprint s
			JOIN Project p ON p.ProjectId = s.ProjectId
			""";

	@Query(nativeQuery = true, value = SELECT_SPRINT + "WHERE s.SprintCode = UUID_TO_BIN(:code) AND s.IsActive = TRUE AND p.IsActive = TRUE")
	Optional<SprintEntity> findByCode(@Param("code") String code);

	@Query(nativeQuery = true, value = SELECT_SPRINT
			+ "WHERE p.ProjectCode = UUID_TO_BIN(:projectCode) AND s.IsActive = TRUE AND p.IsActive = TRUE ORDER BY s.StartDate, s.SprintId")
	List<SprintEntity> findByProject(@Param("projectCode") String projectCode);

	@Query(nativeQuery = true, value = """
			SELECT COUNT(*)
			FROM Sprint s
			JOIN Project p ON p.ProjectId = s.ProjectId
			WHERE p.ProjectCode = UUID_TO_BIN(:projectCode) AND s.IsActive = TRUE
			  AND s.StartDate < :endDate AND s.EndDate > :startDate
			""")
	long countOverlapping(@Param("projectCode") String projectCode, @Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate);

	/** Same as {@link #countOverlapping}, ignoring one sprint: the one being moved must not clash with its old self. */
	@Query(nativeQuery = true, value = """
			SELECT COUNT(*)
			FROM Sprint s
			JOIN Project p ON p.ProjectId = s.ProjectId
			WHERE p.ProjectCode = UUID_TO_BIN(:projectCode) AND s.IsActive = TRUE
			  AND s.SprintCode <> UUID_TO_BIN(:exceptCode)
			  AND s.StartDate < :endDate AND s.EndDate > :startDate
			""")
	long countOverlappingExcept(@Param("projectCode") String projectCode, @Param("exceptCode") String exceptCode,
			@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

	@Query(nativeQuery = true, value = """
			SELECT COUNT(*)
			FROM Sprint s
			JOIN Project p ON p.ProjectId = s.ProjectId
			WHERE p.ProjectCode = UUID_TO_BIN(:projectCode) AND s.IsActive = TRUE AND s.Status = 'ACTIVE'
			""")
	long countActiveInProject(@Param("projectCode") String projectCode);

	/** Nothing is inserted (0 rows) if the project or the creator does not exist. */
	@Modifying(clearAutomatically = true)
	@Query(nativeQuery = true, value = """
			INSERT INTO Sprint (SprintCode, ProjectId, Name, ConfiguredDays, StartDate, EndDate, PlannedVelocity, CreatedBy)
			SELECT UUID_TO_BIN(:code), p.ProjectId, :name, :configuredDays, :startDate, :endDate, :plannedVelocity, u.UserId
			FROM Project p, `User` u
			WHERE p.ProjectCode = UUID_TO_BIN(:projectCode) AND p.IsActive = TRUE
			  AND u.UserCode = UUID_TO_BIN(:createdBy) AND u.IsActive = TRUE AND u.DeletedAt IS NULL
			""")
	int insert(@Param("code") String code, @Param("projectCode") String projectCode, @Param("name") String name,
			@Param("configuredDays") int configuredDays, @Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate, @Param("plannedVelocity") int plannedVelocity,
			@Param("createdBy") String createdBy);

	/** A closed sprint is history: it matches no row. */
	@Modifying(clearAutomatically = true)
	@Query(nativeQuery = true, value = """
			UPDATE Sprint
			SET Name = :name, StartDate = :startDate, EndDate = :endDate, PlannedVelocity = :plannedVelocity
			WHERE SprintCode = UUID_TO_BIN(:code) AND IsActive = TRUE AND Status <> 'CLOSED'
			""")
	int updateDetails(@Param("code") String code, @Param("name") String name, @Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate, @Param("plannedVelocity") int plannedVelocity);

	/**
	 * Day 0 of the burndown: the hours in the sprint on the eve of its first day, kept if it already exists (an early start
	 * has been writing it). The hours are those of {@code SprintRemainingHours} (V1.11), and a sprint without any counts 0.
	 */
	@Modifying(clearAutomatically = true)
	@Query(nativeQuery = true, value = """
			INSERT INTO BurndownData (SprintId, SnapshotDate, RemainingHours, IdealRemainingHours)
			SELECT s.SprintId, DATE_SUB(s.StartDate, INTERVAL 1 DAY), COALESCE(r.RemainingHours, 0), COALESCE(r.RemainingHours, 0)
			FROM Sprint s LEFT JOIN SprintRemainingHours r ON r.SprintId = s.SprintId
			WHERE s.SprintCode = UUID_TO_BIN(:code) AND s.IsActive = TRUE
			ON DUPLICATE KEY UPDATE BurndownData.SprintId = BurndownData.SprintId
			""")
	int insertBurndownBaseline(@Param("code") String code);

	/**
	 * Today's burndown snapshot of an ACTIVE sprint, as the {@code SprintBurndownToday} view (V1.11) describes it, replacing an
	 * earlier one of the same day. workitem-service writes the same statement after every change to the hours; this one is
	 * for the moments this service ends or begins a sprint. A sprint that is not active matches nothing.
	 */
	@Modifying(clearAutomatically = true)
	@Query(nativeQuery = true, value = """
			INSERT INTO BurndownData (SprintId, SnapshotDate, RemainingHours, IdealRemainingHours)
			SELECT b.SprintId, b.SnapshotDate, b.RemainingHours, b.IdealRemainingHours
			FROM SprintBurndownToday b JOIN Sprint s ON s.SprintId = b.SprintId
			WHERE s.SprintCode = UUID_TO_BIN(:code)
			ON DUPLICATE KEY UPDATE RemainingHours = VALUES(RemainingHours), IdealRemainingHours = VALUES(IdealRemainingHours)
			""")
	int snapshotBurndown(@Param("code") String code);

	@Modifying(clearAutomatically = true)
	@Query(nativeQuery = true, value = "UPDATE Sprint SET Status = 'ACTIVE' WHERE SprintCode = UUID_TO_BIN(:code) AND IsActive = TRUE AND Status = 'PLANNED'")
	int start(@Param("code") String code);

	@Modifying(clearAutomatically = true)
	@Query(nativeQuery = true, value = "UPDATE Sprint SET Status = 'CLOSED' WHERE SprintCode = UUID_TO_BIN(:code) AND IsActive = TRUE AND Status = 'ACTIVE'")
	int close(@Param("code") String code);

	/** A closed sprint's velocity is frozen: it matches no row. */
	@Modifying(clearAutomatically = true)
	@Query(nativeQuery = true, value = "UPDATE Sprint SET Velocity = :velocity WHERE SprintCode = UUID_TO_BIN(:code) AND IsActive = TRUE AND Status <> 'CLOSED'")
	int updateVelocity(@Param("code") String code, @Param("velocity") int velocity);

	@Modifying(clearAutomatically = true)
	@Query(nativeQuery = true, value = """
			UPDATE Sprint SET IsActive = FALSE, DeletedAt = NOW()
			WHERE IsActive = TRUE AND ProjectId = (SELECT ProjectId FROM Project WHERE ProjectCode = UUID_TO_BIN(:projectCode))
			""")
	int softDeleteByProject(@Param("projectCode") String projectCode);

}
