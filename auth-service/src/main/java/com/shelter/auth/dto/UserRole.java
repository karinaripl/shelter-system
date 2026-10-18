package com.shelter.auth.dto;

/**
 * Роли системы. Определяют, что пользователь может делать; у пользователя может быть несколько ролей.
 * Список совпадает с ограничением chk_user_roles_role в таблице users_schema.user_roles.
 * Права каждой роли — в docs/access.md.
 */
public enum UserRole {
    ADMIN("Администратор: учётные записи, сотрудники, финансы и полный доступ"),
    VET("Ветеринар: медицина и медицинское состояние животных"),
    CURATOR("Куратор: животные, клетки, фото, склад"),
    ADOPTION_MANAGER("Менеджер по пристройству: визиты, заявки, пристройство, фото, мероприятия"),
    COORDINATOR("Координатор: волонтёры, закрепления, мероприятия"),
    VISITOR("Посетитель: публичный каталог и свои заявки");

    private final String description;

    UserRole(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
