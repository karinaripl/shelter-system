package com.shelter.core.dto;

/** Причина списания (product_write_off.reason). */
public enum WriteOffReason {
    EXPIRED("истёк срок годности"),
    DAMAGED("испорчено"),
    LOST("утеряно"),
    OTHER("другое");

    private final String title;

    WriteOffReason(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
