package com.mapmory.backend.upload.config;

import com.mapmory.backend.upload.domain.UploadPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(UploadPolicyProperties.class)
public class UploadPolicyConfig {

    @Bean
    public UploadPolicy uploadPolicy(UploadPolicyProperties properties) {
        return new UploadPolicy(properties.maxFileSize().toBytes(), properties.maxFilesPerRequest());
    }
}
