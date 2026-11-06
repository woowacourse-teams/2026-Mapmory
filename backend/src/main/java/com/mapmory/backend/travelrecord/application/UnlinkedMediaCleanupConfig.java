package com.mapmory.backend.travelrecord.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(UnlinkedMediaCleanupProperties.class)
class UnlinkedMediaCleanupConfig {
}
