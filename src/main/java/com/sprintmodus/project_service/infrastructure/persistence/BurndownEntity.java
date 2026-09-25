package com.sprintmodus.project_service.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Persistence model of a {@code BurndownData} row. */
@Entity
@Table(name = "BurndownData")
class BurndownEntity {

	@Id
	@Column(name = "BurndownId")
	Long id;

	@Column(name = "SnapshotDate")
	LocalDate snapshotDate;

	@Column(name = "RemainingHours")
	BigDecimal remainingHours;

}
