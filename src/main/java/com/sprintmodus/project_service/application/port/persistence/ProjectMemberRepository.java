package com.sprintmodus.project_service.application.port.persistence;

import java.util.List;
import java.util.UUID;

import com.sprintmodus.project_service.domain.model.ProjectMember;

public interface ProjectMemberRepository {

	/** The active members of an active project, by name. */
	List<ProjectMember> findByProject(UUID projectCode);

}
