package com.sprintmodus.project_service.infrastructure.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ApplicationConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
