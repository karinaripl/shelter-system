package com.shelter.auth.dto;

/**
 * Роли системы. Определяют, что пользователь может делать; у пользователя может быть несколько ролей.
 * Список совпадает с ограничением chk_user_roles_role в таблице users_schema.user_roles.
 */
public enum UserRole {
    ADMIN("Администратор: учётные записи и полный доступ"),
    VET("Ветеринар: медицина"),
    CURATOR("Куратор: животные, клетки, склад"),
    ADOPTION_MANAGER("Менеджер по пристройству: визиты и заявки"),
    COORDINATOR("Координатор: волонтёры, мероприятия, финансы"),
    VISITOR("Посетитель: каталог, запись на знакомство, заявки");

    private final String description;

    UserRole(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
