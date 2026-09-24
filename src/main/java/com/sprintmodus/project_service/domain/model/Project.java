package com.sprintmodus.project_service.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A project. Identified everywhere by its {@code code}; the internal database id never leaves the persistence layer.
 *
 * @param key short display key such as {@code WAR}, unique in the tenant
 * @param description may be {@code null}
 * @param createdBy user code of the creator
 */
public record Project(UUID code, String name, String description, String key, UUID createdBy, Instant createdAt) {
}
