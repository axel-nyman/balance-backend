package org.example.axelnyman.main.infrastructure.config;

import org.example.axelnyman.main.domain.model.User;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class EntityConfiguration {

    private final PasswordEncoder passwordEncoder;

    public EntityConfiguration(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void configureEntities() {
        User.setPasswordEncoder(passwordEncoder);
    }
}