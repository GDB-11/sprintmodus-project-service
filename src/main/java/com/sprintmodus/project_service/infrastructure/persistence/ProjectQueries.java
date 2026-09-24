package com.sprintmodus.project_service.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Every SQL statement on {@code Project}, written out. The interface extends the bare {@link Repository} marker on
 * purpose, so no generated query is available: each method is a native query. Writes must run in the caller's
 * transaction. UUIDs are {@code BINARY(16)}: written with {@code UUID_TO_BIN()}, read into a {@code UUID}.
 * <p>
 * Every {@code @Modifying} query clears the persistence context: a native query that returns an entity hands back the
 * instance already loaded in the transaction if there is one, so without it a read after an update would be stale.
 */
interface ProjectQueries extends Repository<ProjectEntity, Long> {

	String SELECT_PROJECT = """
			SELECT p.ProjectId, p.ProjectCode, p.Name, p.Description, p.`Key` AS ProjectKey,
			       u.UserCode AS CreatedByCode, p.CreatedAt
			FROM Project p
			JOIN `User` u ON u.UserId = p.CreatedBy
			""";

	@Query(nativeQuery = true, value = SELECT_PROJECT + "WHERE p.ProjectCode = UUID_TO_BIN(:code) AND p.IsActive = TRUE")
	Optional<ProjectEntity> findByCode(@Param("code") String code);

	@Query(nativeQuery = true, value = SELECT_PROJECT + "WHERE p.IsActive = TRUE ORDER BY p.Name, p.ProjectId")
	List<ProjectEntity> findAllActive();

	@Query(nativeQuery = true, value = "SELECT COUNT(*) FROM Project WHERE IsActive = TRUE")
	long countActive();

	/** Deleted projects count: a key stays reserved. The column's collation is case-insensitive. */
	@Query(nativeQuery = true, value = "SELECT COUNT(*) FROM Project WHERE `Key` = :key")
	long countByKey(@Param("key") String key);

	/**
	 * Locks the active project's row until the transaction ends, or returns nothing if there is none. Used to serialize
	 * the changes to a project's sprints.
	 */
	@Query(nativeQuery = true, value = "SELECT ProjectId FROM Project WHERE ProjectCode = UUID_TO_BIN(:code) AND IsActive = TRUE FOR UPDATE")
	Optional<Long> lockByCode(@Param("code") String code);

	/**
	 * The creator is looked up by user code; nothing is inserted (0 rows) if that user is not active. A blank description
	 * is stored as NULL.
	 */
	@Modifying(clearAutomatically = true)
	@Query(nativeQuery = true, value = """
			INSERT INTO Project (ProjectCode, Name, Description, `Key`, CreatedBy)
			SELECT UUID_TO_BIN(:code), :name, NULLIF(:description, ''), :key, u.UserId
			FROM `User` u
			WHERE u.UserCode = UUID_TO_BIN(:createdBy) AND u.IsActive = TRUE AND u.DeletedAt IS NULL
			""")
	int insert(@Param("code") String code, @Param("name") String name, @Param("description") String description,
			@Param("key") String key, @Param("createdBy") String createdBy);

	@Modifying(clearAutomatically = true)
	@Query(nativeQuery = true, value = """
			UPDATE Project SET Name = :name, Description = NULLIF(:description, '')
			WHERE ProjectCode = UUID_TO_BIN(:code) AND IsActive = TRUE
			""")
	int updateDetails(@Param("code") String code, @Param("name") String name, @Param("description") String description);

	@Modifying(clearAutomatically = true)
	@Query(nativeQuery = true, value = "UPDATE Project SET IsActive = FALSE, DeletedAt = NOW() WHERE ProjectCode = UUID_TO_BIN(:code) AND IsActive = TRUE")
	int softDelete(@Param("code") String code);

}
