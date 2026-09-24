package com.sprintmodus.project_service.infrastructure.persistence;

import java.time.DayOfWeek;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Persistence model of the tenant's single {@code TenantSprintConfig} row. */
@Entity
@Table(name = "TenantSprintConfig")
class TenantSprintConfigEntity {

	@Id
	@Column(name = "ConfigId")
	Integer id;

	@Column(name = "DefaultSprintDays")
	int defaultSprintDays;

	@Enumerated(EnumType.STRING)
	@Column(name = "SprintStartDay")
	DayOfWeek sprintStartDay;

	@Column(name = "VelocityTrackingEnabled")
	boolean velocityTrackingEnabled;

}
