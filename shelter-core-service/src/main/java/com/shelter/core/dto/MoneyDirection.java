package com.shelter.core.dto;

/** Направление денежной операции (finance_transaction.direction). */
public enum MoneyDirection {
    INCOME("приход"),
    EXPENSE("расход");

    private final String title;

    MoneyDirection(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
