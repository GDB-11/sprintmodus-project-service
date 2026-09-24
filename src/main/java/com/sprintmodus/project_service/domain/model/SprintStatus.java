package com.sprintmodus.project_service.domain.model;

/** Lifecycle of a sprint: PLANNED, then ACTIVE (started), then CLOSED (finished, its velocity frozen). */
public enum SprintStatus {

	PLANNED,
	ACTIVE,
	CLOSED

}
