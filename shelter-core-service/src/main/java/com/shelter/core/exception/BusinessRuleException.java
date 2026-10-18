package com.shelter.core.exception;

/** Нарушение бизнес-правила (переполненная клетка, недопустимый переход статуса и т. п.) — 409. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
