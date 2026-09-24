package com.sprintmodus.project_service.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.sprintmodus.project_service.application.port.persistence.ProjectMemberRepository;
import com.sprintmodus.project_service.domain.model.ProjectMember;

@Repository
class JpaProjectMemberRepository implements ProjectMemberRepository {

	private final ProjectMemberQueries members;

	JpaProjectMemberRepository(ProjectMemberQueries members) {
		this.members = members;
	}

	@Override
	public List<ProjectMember> findByProject(UUID projectCode) {
		return members.findByProject(projectCode.toString()).stream()
				.map(entity -> new ProjectMember(entity.userCode, entity.fullName, entity.email, entity.role)).toList();
	}

}
