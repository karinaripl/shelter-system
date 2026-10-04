package com.shelter.auth.mapper;

import com.shelter.auth.dto.UserResponse;
import com.shelter.auth.dto.UserRole;
import com.shelter.model.generated.auth.tables.records.UsersRecord;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class UserMapper {

    public UserResponse toResponse(UsersRecord record, Set<UserRole> roles) {
        return new UserResponse(
                record.getId(),
                record.getUsername(),
                record.getEmail(),
                roles.stream().sorted().toList(),
                record.getEnabled(),
                record.getCreatedAt()
        );
    }
}
