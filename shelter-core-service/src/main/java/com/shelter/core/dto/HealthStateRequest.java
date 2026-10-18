package com.shelter.core.dto;

import jakarta.validation.constraints.NotNull;

public record HealthStateRequest(

        @NotNull(message = "Медицинское состояние обязательно: HEALTHY, QUARANTINE или TREATMENT")
        HealthState healthState
) {
}
