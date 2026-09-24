package com.sprintmodus.project_service.application.port.persistence;

import com.sprintmodus.project_service.domain.model.SprintConfig;

/** The tenant's single sprint configuration (created with defaults when the tenant database is). */
public interface SprintConfigRepository {

	SprintConfig find();

	SprintConfig save(SprintConfig config);

}
