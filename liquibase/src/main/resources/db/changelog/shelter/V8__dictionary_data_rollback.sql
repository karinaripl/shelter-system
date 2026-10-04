DELETE FROM shelter_schema.product_category
WHERE name IN ('Корм', 'Наполнитель', 'Медицина', 'Инвентарь');

DELETE FROM shelter_schema.position
WHERE name IN ('Директор', 'Ветеринар', 'Администратор', 'Менеджер по пристройству', 'Фандрайзер',
               'Координатор волонтёров', 'Бухгалтер', 'Зоопсихолог', 'Кинолог', 'Уборщик');

DELETE FROM shelter_schema.procedure_type
WHERE name IN ('Осмотр', 'Вакцинация', 'Стерилизация', 'Кастрация', 'Дегельминтизация',
               'Обработка от блох', 'Чистка ушей', 'Стрижка когтей', 'Лечение зубов', 'Анализ крови');

DELETE FROM shelter_schema.arrival_source
WHERE code IN ('STREET', 'ABANDONED', 'FROM_OWNER', 'OTHER_SHELTER', 'BORN_IN_SHELTER', 'UNKNOWN');

DELETE FROM shelter_schema.animal_status
WHERE code IN ('IN_SHELTER', 'FOSTER', 'ADOPTED', 'DIED', 'ESCAPED', 'TRANSFERRED');
