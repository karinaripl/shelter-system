package com.shelter.auth.service;

import com.shelter.auth.dto.CreateUserRequest;
import com.shelter.auth.dto.RoleResponse;
import com.shelter.auth.dto.UserResponse;
import com.shelter.auth.dto.UserRole;
import com.shelter.auth.exception.OperationNotAllowedException;
import com.shelter.auth.exception.UserAlreadyExistsException;
import com.shelter.auth.exception.UserNotFoundException;
import com.shelter.auth.mapper.UserMapper;
import com.shelter.auth.repository.UserRepository;
import com.shelter.model.generated.auth.tables.records.UsersRecord;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       UserMapper userMapper,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Создаёт пользователя с заданными ролями (минимум одна).
     * Используется при регистрации посетителя, создании сотрудника и первого администратора.
     */
    @Transactional
    public UserResponse createUser(String username, String email, String password, Set<UserRole> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new OperationNotAllowedException("У пользователя должна быть хотя бы одна роль");
        }
        String normalizedEmail = normalizeEmail(email);
        String normalizedUsername = username.trim();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new UserAlreadyExistsException("Пользователь с таким email уже существует");
        }
        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new UserAlreadyExistsException("Пользователь с таким именем уже существует");
        }

        UsersRecord saved;
        try {
            saved = userRepository.insert(normalizedUsername, normalizedEmail, passwordEncoder.encode(password));
        } catch (DuplicateKeyException e) {
            // Параллельная регистрация с теми же данными успела раньше
            throw new UserAlreadyExistsException("Пользователь с таким email или именем уже существует");
        }

        Set<UserRole> savedRoles = EnumSet.copyOf(roles);
        userRepository.replaceRoles(saved.getId(), savedRoles);
        return userMapper.toResponse(saved, savedRoles);
    }

    // Свой @Transactional: вызов createUser изнутри класса проходит мимо прокси Spring
    @Transactional
    public UserResponse create(CreateUserRequest request) {
        return createUser(request.username(), request.email(), request.password(), request.roles());
    }

    public UserResponse findById(Long id) {
        UsersRecord user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        return userMapper.toResponse(user, userRepository.findRoles(id));
    }

    public List<UserResponse> findAll() {
        List<UsersRecord> users = userRepository.findAll();
        Map<Long, Set<UserRole>> roles = userRepository.findRolesByUserIds(
                users.stream().map(UsersRecord::getId).toList());
        return users.stream()
                .map(user -> userMapper.toResponse(user,
                        roles.getOrDefault(user.getId(), EnumSet.noneOf(UserRole.class))))
                .toList();
    }

    @Transactional
    public UserResponse updateRoles(Long currentUserId, Long id, Set<UserRole> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new OperationNotAllowedException("У пользователя должна быть хотя бы одна роль");
        }
        UsersRecord user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        // Иначе можно остаться без единого администратора
        if (id.equals(currentUserId) && !roles.contains(UserRole.ADMIN)) {
            throw new OperationNotAllowedException("Нельзя снять роль ADMIN с самого себя");
        }

        Set<UserRole> newRoles = EnumSet.copyOf(roles);
        userRepository.replaceRoles(id, newRoles);
        return userMapper.toResponse(user, newRoles);
    }

    @Transactional
    public UserResponse updateStatus(Long currentUserId, Long id, boolean enabled) {
        if (userRepository.findById(id).isEmpty()) {
            throw new UserNotFoundException(id);
        }
        if (id.equals(currentUserId) && !enabled) {
            throw new OperationNotAllowedException("Нельзя заблокировать самого себя");
        }
        userRepository.updateEnabled(id, enabled);
        return findById(id);
    }

    public List<RoleResponse> findAllRoles() {
        return Arrays.stream(UserRole.values())
                .map(role -> new RoleResponse(role.name(), role.getDescription()))
                .toList();
    }

    /** Email хранится и ищется в нижнем регистре без пробелов по краям. */
    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
