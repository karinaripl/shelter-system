package com.shelter.auth.repository;

import com.shelter.model.generated.auth.tables.records.UsersRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static com.shelter.model.generated.auth.tables.Users.USERS;

@Repository
public class UserRepository {

    private final DSLContext dsl;

    public UserRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public UsersRecord save(UsersRecord user) {
        return dsl.insertInto(USERS)
                .set(USERS.USERNAME, user.getUsername())
                .set(USERS.EMAIL, user.getEmail())
                .set(USERS.PASSWORD_HASH, user.getPasswordHash())
                .set(USERS.ROLE, user.getRole())
                .returning()
                .fetchOne();
    }

    public Optional<UsersRecord> findByEmail(String email) {
        return dsl.selectFrom(USERS)
                .where(USERS.EMAIL.eq(email))
                .fetchOptional();
    }
}