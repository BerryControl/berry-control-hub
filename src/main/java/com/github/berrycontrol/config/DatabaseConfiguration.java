package com.github.berrycontrol.config;

import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@EnableJpaRepositories(basePackages = "com.github.berrycontrol.persistence.repository")
public class DatabaseConfiguration {
}
