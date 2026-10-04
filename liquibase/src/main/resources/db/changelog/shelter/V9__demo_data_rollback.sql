-- Откат демонстрационных данных: очищает все рабочие таблицы схемы (справочники не трогает).
-- Контекст demo применяется только к демонстрационной базе, поэтому других данных там нет
TRUNCATE TABLE
    shelter_schema.event_participation,
    shelter_schema.event,
    shelter_schema.product_write_off,
    shelter_schema.product_expense,
    shelter_schema.procedure_consumption_rate,
    shelter_schema.procedure_record,
    shelter_schema.medical_record,
    shelter_schema.medical_card,
    shelter_schema.batch_stock,
    shelter_schema.product_batch,
    shelter_schema.supply,
    shelter_schema.supplier,
    shelter_schema.warehouse,
    shelter_schema.product,
    shelter_schema.salary_payment,
    shelter_schema.finance_transaction,
    shelter_schema.donation,
    shelter_schema.benefactor,
    shelter_schema.volunteer_assignment,
    shelter_schema.animal,
    shelter_schema.cage,
    shelter_schema.volunteer,
    shelter_schema.employee
RESTART IDENTITY;
