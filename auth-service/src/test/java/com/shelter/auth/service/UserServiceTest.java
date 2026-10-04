package com.shelter.auth.service;

import com.shelter.auth.dto.UserResponse;
import com.shelter.auth.dto.UserRole;
import com.shelter.auth.exception.OperationNotAllowedException;
import com.shelter.auth.exception.UserAlreadyExistsException;
import com.shelter.auth.mapper.UserMapper;
import com.shelter.auth.repository.UserRepository;
import com.shelter.model.generated.auth.tables.records.UsersRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private UserRepository userRepository;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        userService = new UserService(userRepository, new UserMapper(), passwordEncoder);
    }

    @Test
    void createUserSavesAllRolesAndNormalizesEmail() {
        when(userRepository.insert("vet", "doc@shelter.local", "hash")).thenReturn(user(5L, "doc@shelter.local"));

        UserResponse created = userService.createUser("  vet ", " Doc@Shelter.Local ", "password1",
                Set.of(UserRole.VET, UserRole.CURATOR));

        assertThat(created.email()).isEqualTo("doc@shelter.local");
        assertThat(created.roles()).containsExactly(UserRole.VET, UserRole.CURATOR);
        verify(userRepository).replaceRoles(5L, Set.of(UserRole.VET, UserRole.CURATOR));
    }

    @Test
    void userCannotBeCreatedWithoutRoles() {
        assertThatThrownBy(() -> userService.createUser("u", "u@shelter.local", "password1", Set.of()))
                .isInstanceOf(OperationNotAllowedException.class);
        verify(userRepository, never()).insert(anyString(), anyString(), anyString());
    }

    @Test
    void duplicateEmailIsRejected() {
        when(userRepository.existsByEmail("taken@shelter.local")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser("u", "taken@shelter.local", "password1",
                Set.of(UserRole.VISITOR)))
                .isInstanceOf(UserAlreadyExistsException.class);
    }

    @Test
    void adminCannotRemoveOwnAdminRole() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "admin@shelter.local")));

        assertThatThrownBy(() -> userService.updateRoles(1L, 1L, Set.of(UserRole.VET)))
                .isInstanceOf(OperationNotAllowedException.class);
        verify(userRepository, never()).replaceRoles(any(), any());
    }

    @Test
    void adminCanChangeRolesOfAnotherUser() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "doc@shelter.local")));

        UserResponse updated = userService.updateRoles(1L, 2L, Set.of(UserRole.ADMIN, UserRole.VET));

        assertThat(updated.roles()).containsExactly(UserRole.ADMIN, UserRole.VET);
        verify(userRepository).replaceRoles(eq(2L), eq(Set.of(UserRole.ADMIN, UserRole.VET)));
    }

    @Test
    void adminCannotBlockThemselves() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "admin@shelter.local")));

        assertThatThrownBy(() -> userService.updateStatus(1L, 1L, false))
                .isInstanceOf(OperationNotAllowedException.class);
        verify(userRepository, never()).updateEnabled(any(), eq(false));
    }

    private static UsersRecord user(Long id, String email) {
        UsersRecord record = new UsersRecord();
        record.setId(id);
        record.setUsername("user" + id);
        record.setEmail(email);
        record.setPasswordHash("hash");
        record.setEnabled(true);
        return record;
    }
}
