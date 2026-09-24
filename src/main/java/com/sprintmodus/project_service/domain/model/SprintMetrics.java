package com.sprintmodus.project_service.domain.model;

/**
 * What the work items of a sprint add up to, derived from the items themselves. Effort points count PBIs and Bugs only;
 * "completed" means the item's status is terminal.
 */
public record SprintMetrics(int totalItems, int completedItems, int plannedEffort, int completedEffort, int defectCount) {

	public static SprintMetrics none() {
		return new SprintMetrics(0, 0, 0, 0, 0);
	}

}
