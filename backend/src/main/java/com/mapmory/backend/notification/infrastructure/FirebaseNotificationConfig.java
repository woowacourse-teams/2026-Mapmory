package com.mapmory.backend.notification.infrastructure;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
        prefix = "notification.fcm",
        name = "enabled",
        havingValue = "true"
)
public class FirebaseNotificationConfig {

    @Bean(destroyMethod = "delete")
    public FirebaseApp notificationFirebaseApp(
            @Value("${notification.fcm.project-id}") final String projectId
    ) throws IOException {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalStateException("FCM 프로젝트 ID가 필요합니다.");
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.getApplicationDefault())
                .setProjectId(projectId)
                .build();

        return FirebaseApp.initializeApp(options, "notification");
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(
            final FirebaseApp notificationFirebaseApp
    ) {
        return FirebaseMessaging.getInstance(notificationFirebaseApp);
    }
}