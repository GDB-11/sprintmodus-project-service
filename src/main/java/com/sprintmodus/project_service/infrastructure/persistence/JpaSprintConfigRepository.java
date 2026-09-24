package com.sprintmodus.project_service.infrastructure.persistence;

import java.time.DayOfWeek;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.sprintmodus.project_service.application.port.persistence.SprintConfigRepository;
import com.sprintmodus.project_service.domain.model.SprintConfig;

@Repository
class JpaSprintConfigRepository implements SprintConfigRepository {

	/** What a tenant database is created with; only used if the configuration row is somehow missing. */
	private static final SprintConfig DEFAULTS = new SprintConfig(14, DayOfWeek.MONDAY, true);

	private final TenantSprintConfigQueries configs;

	private final TransactionTemplate transaction;

	JpaSprintConfigRepository(TenantSprintConfigQueries configs, PlatformTransactionManager transactionManager) {
		this.configs = configs;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	@Override
	public SprintConfig find() {
		return configs.find().map(JpaSprintConfigRepository::toDomain).orElse(DEFAULTS);
	}

	@Override
	public SprintConfig save(SprintConfig config) {
		return transaction.execute(_ -> {
			if (configs.update(config.defaultSprintDays(), config.sprintStartDay().name(), config.velocityTrackingEnabled()) == 0) {
				throw new IllegalStateException("The tenant has no sprint configuration row");
			}
			return find();
		});
	}

	private static SprintConfig toDomain(TenantSprintConfigEntity entity) {
		return new SprintConfig(entity.defaultSprintDays, entity.sprintStartDay, entity.velocityTrackingEnabled);
	}

}
