package com.sprintmodus.project_service.adapter.rest.controller;

import com.sprintmodus.common_lib.security.AuthenticatedUser;
import com.sprintmodus.project_service.application.dto.Actor;
import com.sprintmodus.project_service.domain.model.OrganizationRole;

final class Actors {

	private Actors() {
	}

	/** The caller, as the use cases see them, from the JWT the security filter verified. */
	static Actor from(AuthenticatedUser user) {
		return new Actor(user.userCode(), OrganizationRole.parse(user.role()));
	}

}
