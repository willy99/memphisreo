package com.memphisreo.common;

import java.util.List;

/**
 * Помилки валідації з прив'язкою до полів форми — фронт підсвічує кожне поле
 * окремо. {@code field} — шлях поля як у формі ("address.city", "rooms"),
 * {@code code} — машинний код ("required", "invalid", ...), текст — на фронті (i18n).
 */
public class ValidationException extends RuntimeException {

    public record FieldError(String field, String code) {
    }

    private final List<FieldError> errors;

    public ValidationException(String message, List<FieldError> errors) {
        super(message);
        this.errors = List.copyOf(errors);
    }

    public List<FieldError> getErrors() {
        return errors;
    }
}
