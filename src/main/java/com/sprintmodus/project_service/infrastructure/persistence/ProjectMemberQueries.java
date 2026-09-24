package com.sprintmodus.project_service.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Every SQL statement on {@code ProjectMember} (native queries only). */
interface ProjectMemberQueries extends Repository<ProjectMemberEntity, Long> {

	@Query(nativeQuery = true, value = """
			SELECT pm.ProjectMemberId, u.UserCode, u.FullName, u.Email, u.Role
			FROM ProjectMember pm
			JOIN Project p ON p.ProjectId = pm.ProjectId
			JOIN `User` u ON u.UserId = pm.UserId
			WHERE p.ProjectCode = UUID_TO_BIN(:projectCode) AND p.IsActive = TRUE
			  AND pm.IsActive = TRUE AND u.IsActive = TRUE AND u.DeletedAt IS NULL
			ORDER BY u.FullName, pm.ProjectMemberId
			""")
	List<ProjectMemberEntity> findByProject(@Param("projectCode") String projectCode);

	@Modifying(clearAutomatically = true)
	@Query(nativeQuery = true, value = """
			INSERT INTO ProjectMember (ProjectId, UserId)
			SELECT p.ProjectId, u.UserId
			FROM Project p, `User` u
			WHERE p.ProjectCode = UUID_TO_BIN(:projectCode) AND u.UserCode = UUID_TO_BIN(:userCode)
			""")
	int insert(@Param("projectCode") String projectCode, @Param("userCode") String userCode);

}
