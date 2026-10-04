package com.shelter.auth.repository;

import com.shelter.auth.dto.UserRole;
import com.shelter.model.generated.auth.tables.records.UsersRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.shelter.model.generated.auth.tables.UserRoles.USER_ROLES;
import static com.shelter.model.generated.auth.tables.Users.USERS;

@Repository
public class UserRepository {

    private final DSLContext dsl;

    public UserRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public UsersRecord insert(String username, String email, String passwordHash) {
        return dsl.insertInto(USERS)
                .set(USERS.USERNAME, username)
                .set(USERS.EMAIL, email)
                .set(USERS.PASSWORD_HASH, passwordHash)
                .returning()
                .fetchOne();
    }

    public Optional<UsersRecord> findById(Long id) {
        return dsl.selectFrom(USERS)
                .where(USERS.ID.eq(id))
                .fetchOptional();
    }

    public Optional<UsersRecord> findByEmail(String email) {
        return dsl.selectFrom(USERS)
                .where(USERS.EMAIL.eq(email))
                .fetchOptional();
    }

    public List<UsersRecord> findAll() {
        return dsl.selectFrom(USERS)
                .orderBy(USERS.ID)
                .fetch();
    }

    public boolean existsByEmail(String email) {
        return dsl.fetchExists(USERS, USERS.EMAIL.eq(email));
    }

    public boolean existsByUsername(String username) {
        return dsl.fetchExists(USERS, USERS.USERNAME.eq(username));
    }

    public void updatePassword(Long id, String passwordHash) {
        dsl.update(USERS)
                .set(USERS.PASSWORD_HASH, passwordHash)
                .where(USERS.ID.eq(id))
                .execute();
    }

    public void updateEnabled(Long id, boolean enabled) {
        dsl.update(USERS)
                .set(USERS.ENABLED, enabled)
                .where(USERS.ID.eq(id))
                .execute();
    }

    // ---------- роли ----------

    public Set<UserRole> findRoles(Long userId) {
        Set<UserRole> roles = EnumSet.noneOf(UserRole.class);
        dsl.select(USER_ROLES.ROLE)
                .from(USER_ROLES)
                .where(USER_ROLES.USER_ID.eq(userId))
                .fetch(USER_ROLES.ROLE)
                .forEach(role -> roles.add(UserRole.valueOf(role)));
        return roles;
    }

    /** Роли сразу нескольких пользователей одним запросом: userId → роли. */
    public Map<Long, Set<UserRole>> findRolesByUserIds(Collection<Long> userIds) {
        Map<Long, Set<UserRole>> result = new HashMap<>();
        dsl.select(USER_ROLES.USER_ID, USER_ROLES.ROLE)
                .from(USER_ROLES)
                .where(USER_ROLES.USER_ID.in(userIds))
                .forEach(r -> result
                        .computeIfAbsent(r.value1(), id -> EnumSet.noneOf(UserRole.class))
                        .add(UserRole.valueOf(r.value2())));
        return result;
    }

    /** Заменяет роли пользователя целиком. Вызывать внутри транзакции. */
    public void replaceRoles(Long userId, Set<UserRole> roles) {
        dsl.deleteFrom(USER_ROLES)
                .where(USER_ROLES.USER_ID.eq(userId))
                .execute();

        var insert = dsl.insertInto(USER_ROLES, USER_ROLES.USER_ID, USER_ROLES.ROLE);
        for (UserRole role : roles) {
            insert = insert.values(userId, role.name());
        }
        insert.execute();
    }

    public boolean existsUserWithRole(UserRole role) {
        return dsl.fetchExists(USER_ROLES, USER_ROLES.ROLE.eq(role.name()));
    }
}
