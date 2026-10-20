package com.hyped.app.notification.infrastructure;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.hyped.app.notification.application.port.out.PushSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "hyped.push", name = "enabled", havingValue = "true")
public class PushConfiguration {
    @Bean
    @ConditionalOnMissingBean(PushSender.class)
    PushSender pushSender(FirebaseApp firebaseIdentityApp) {
        return new FirebasePushSender(FirebaseMessaging.getInstance(firebaseIdentityApp));
    }
}
