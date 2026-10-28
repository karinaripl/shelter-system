package com.shelter.core.dto;

/** Вид пожертвования (donation.donation_type): деньги или товары. */
public enum DonationType {
    MONEY("денежное"),
    GOODS("натуральное");

    private final String title;

    DonationType(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
