package com.shelter.core.security;

/**
 * Роли и выражения для @PreAuthorize. Источник правил — docs/access.md.
 */
public final class Roles {

    public static final String ADMIN = "ADMIN";
    public static final String VET = "VET";
    public static final String CURATOR = "CURATOR";
    public static final String ADOPTION_MANAGER = "ADOPTION_MANAGER";
    public static final String COORDINATOR = "COORDINATOR";

    /** Любой сотрудник приюта (все роли, кроме VISITOR). */
    public static final String STAFF =
            "hasAnyRole('ADMIN', 'VET', 'CURATOR', 'ADOPTION_MANAGER', 'COORDINATOR')";

    /** Животные и клетки: изменение. */
    public static final String ANIMALS_WRITE = "hasAnyRole('ADMIN', 'CURATOR')";

    /** Медицинское состояние животного. */
    public static final String HEALTH_WRITE = "hasAnyRole('ADMIN', 'VET')";

    /** Полная медкарта: осмотры, диагнозы, процедуры, флаги. */
    public static final String MEDICAL = "hasAnyRole('ADMIN', 'VET')";

    /** Виды процедур и нормы расхода: чтение. */
    public static final String PROCEDURE_TYPES_READ = "hasAnyRole('ADMIN', 'VET', 'CURATOR')";

    /** Виды процедур и нормы расхода: изменение. */
    public static final String PROCEDURE_TYPES_WRITE = "hasAnyRole('ADMIN', 'VET')";

    /** Выбытие:ветеринар — только «умер» (проверяется в сервисе). */
    public static final String DEPARTURE_WRITE = "hasAnyRole('ADMIN', 'CURATOR', 'VET')";

    /** Фото животных: изменение. */
    public static final String PHOTOS_WRITE = "hasAnyRole('ADMIN', 'CURATOR', 'ADOPTION_MANAGER')";

    /** Админ-панель. */
    public static final String ADMIN_ONLY = "hasRole('ADMIN')";

    private Roles() {
    }
}
