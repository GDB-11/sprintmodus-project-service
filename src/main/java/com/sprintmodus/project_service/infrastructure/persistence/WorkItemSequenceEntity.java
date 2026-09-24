package com.sprintmodus.project_service.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Persistence model of {@code WorkItemSequence}. project-service only creates a project's row; work items use it. */
@Entity
@Table(name = "WorkItemSequence")
class WorkItemSequenceEntity {

	@Id
	@Column(name = "SequenceId")
	Long id;

}
