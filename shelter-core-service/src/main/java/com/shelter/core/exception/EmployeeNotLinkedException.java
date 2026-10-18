package com.shelter.core.exception;

/** Действие записывает сотрудника (employee_id), но учётная запись не связана с карточкой сотрудника — 409. */
public class EmployeeNotLinkedException extends BusinessRuleException {

    public EmployeeNotLinkedException() {
        super("Ваша учётная запись не связана с карточкой сотрудника. Обратитесь к администратору");
    }
}
