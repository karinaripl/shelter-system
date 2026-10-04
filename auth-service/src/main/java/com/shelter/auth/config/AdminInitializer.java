package com.shelter.auth.config;

import com.shelter.auth.dto.UserRole;
import com.shelter.auth.repository.UserRepository;
import com.shelter.auth.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Создаёт первого администратора при запуске, если в системе ещё нет ни одного ADMIN.
 * Без этого создавать сотрудников было бы некому: регистрация даёт только роль VISITOR.
 */
@Component
public class AdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    private final UserRepository userRepository;
    private final UserService userService;

    @Value("${admin.bootstrap.username}")
    private String username;

    @Value("${admin.bootstrap.email}")
    private String email;

    @Value("${admin.bootstrap.password}")
    private String password;

    public AdminInitializer(UserRepository userRepository, UserService userService) {
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            log.info("ADMIN_EMAIL/ADMIN_PASSWORD не заданы — первый администратор не создаётся");
            return;
        }
        if (userRepository.existsUserWithRole(UserRole.ADMIN)) {
            return;
        }
        if (userRepository.existsByEmail(UserService.normalizeEmail(email))) {
            log.warn("Администратора нет, но email {} уже занят другим пользователем — "
                    + "назначьте роль ADMIN вручную или укажите другой ADMIN_EMAIL", email);
            return;
        }

        userService.createUser(username, email, password, Set.of(UserRole.ADMIN));
        log.info("Создан первый администратор: {}", email);
    }
}
