package com.shelter.core.dto;

/**
 * Категория денежной операции (finance_transaction.category). DONATION, SALARY и PURCHASE создаются и удаляются
 * только через свои документы (пожертвование, начисление зарплаты, оплата поставки); вручную — только OTHER.
 */
public enum FinanceCategory {
    DONATION("пожертвование"),
    SALARY("зарплата"),
    PURCHASE("закупка"),
    OTHER("прочее");

    private final String title;

    FinanceCategory(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
