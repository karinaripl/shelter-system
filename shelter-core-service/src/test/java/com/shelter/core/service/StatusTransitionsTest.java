package com.shelter.core.service;

import org.junit.jupiter.api.Test;

import static com.shelter.core.dto.AnimalStatusCode.ADOPTED;
import static com.shelter.core.dto.AnimalStatusCode.AVAILABLE;
import static com.shelter.core.dto.AnimalStatusCode.DIED;
import static com.shelter.core.dto.AnimalStatusCode.FOSTER;
import static com.shelter.core.dto.AnimalStatusCode.IN_SHELTER;
import static com.shelter.core.dto.AnimalStatusCode.RESERVED;
import static org.assertj.core.api.Assertions.assertThat;

class StatusTransitionsTest {

    @Test
    void lifecycleGoesInShelterAvailableReserved() {
        assertThat(StatusTransitions.isAllowed(IN_SHELTER, AVAILABLE)).isTrue();
        assertThat(StatusTransitions.isAllowed(AVAILABLE, RESERVED)).isTrue();
        assertThat(StatusTransitions.isAllowed(RESERVED, AVAILABLE)).isTrue();
        assertThat(StatusTransitions.isAllowed(AVAILABLE, IN_SHELTER)).isTrue();
    }

    @Test
    void fosterIsReachableAndReturnable() {
        assertThat(StatusTransitions.isAllowed(IN_SHELTER, FOSTER)).isTrue();
        assertThat(StatusTransitions.isAllowed(AVAILABLE, FOSTER)).isTrue();
        assertThat(StatusTransitions.isAllowed(FOSTER, IN_SHELTER)).isTrue();
        assertThat(StatusTransitions.isAllowed(FOSTER, AVAILABLE)).isTrue();
    }

    @Test
    void forbiddenTransitions() {
        assertThat(StatusTransitions.isAllowed(IN_SHELTER, RESERVED)).as("бронь только из «готов»").isFalse();
        assertThat(StatusTransitions.isAllowed(FOSTER, RESERVED)).isFalse();
        assertThat(StatusTransitions.isAllowed(RESERVED, FOSTER)).isFalse();
        assertThat(StatusTransitions.isAllowed(ADOPTED, AVAILABLE)).as("из итогового — никуда").isFalse();
        assertThat(StatusTransitions.isAllowed(DIED, IN_SHELTER)).isFalse();
    }

    @Test
    void reservationNeedsAdmin() {
        assertThat(StatusTransitions.requiresAdmin(AVAILABLE, RESERVED)).isTrue();
        assertThat(StatusTransitions.requiresAdmin(RESERVED, AVAILABLE)).isTrue();
        assertThat(StatusTransitions.requiresAdmin(IN_SHELTER, AVAILABLE)).isFalse();
    }

    @Test
    void adoptionOnlyFromAvailableOrReserved() {
        assertThat(StatusTransitions.canBeAdoptedFrom(AVAILABLE)).isTrue();
        assertThat(StatusTransitions.canBeAdoptedFrom(RESERVED)).isTrue();
        assertThat(StatusTransitions.canBeAdoptedFrom(IN_SHELTER)).isFalse();
        assertThat(StatusTransitions.canBeAdoptedFrom(FOSTER)).isFalse();
    }
}
