package com.neuroflow.backend.config;

import com.neuroflow.backend.domain.AppUser;
import com.neuroflow.backend.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds demo users into PostgreSQL when the table is empty.
 */
@Component
public class DemoUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;
    private final String userUsername;
    private final String userPassword;

    public DemoUserSeeder(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            @Value("${neuroflow.security.admin-username:admin}") String adminUsername,
            @Value("${neuroflow.security.admin-password:admin123}") String adminPassword,
            @Value("${neuroflow.security.user-username:user}") String userUsername,
            @Value("${neuroflow.security.user-password:user123}") String userPassword) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.userUsername = userUsername;
        this.userPassword = userPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (appUserRepository.count() > 0) {
            return;
        }
        appUserRepository.save(build(adminUsername, adminPassword, "ADMIN,USER"));
        appUserRepository.save(build(userUsername, userPassword, "USER"));
        log.info("Seeded demo users into PostgreSQL (admin + user)");
    }

    private AppUser build(String username, String rawPassword, String roles) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRoles(roles);
        user.setEnabled(true);
        return user;
    }
}
