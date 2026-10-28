package com.shelter.core.service;

import com.shelter.core.client.AuthAccounts;
import com.shelter.core.client.AuthUser;
import com.shelter.core.dto.AccountInfo;
import com.shelter.core.dto.AccountRequest;
import com.shelter.core.dto.EmployeeAdminResponse;
import com.shelter.core.dto.EmployeeCreateRequest;
import com.shelter.core.dto.EmployeeDetailsResponse;
import com.shelter.core.dto.EmployeeRequest;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.exception.ServiceUnavailableException;
import com.shelter.core.repository.EmployeeRepository;
import com.shelter.core.repository.EmployeeRepository.Status;
import com.shelter.core.repository.PositionRepository;
import com.shelter.core.repository.SalaryRepository;
import com.shelter.core.security.CurrentUser;
import com.shelter.model.generated.shelter.tables.records.EmployeeRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.shelter.core.service.MedicalChecks.trimToNull;

/**
 * Сотрудники в админ-панели (только ADMIN): карточка с персональными данными, учётная запись, увольнение.
 *
 * Карточка живёт в shelter_schema, учётная запись — в auth-service (другая база), поэтому общей транзакции нет.
 * Создание с учётной записью: сначала все локальные проверки, затем запись в auth-service по ключу запроса
 * (Idempotency-Key), затем карточка в своей транзакции; если карточка не сохранилась — компенсирующее удаление
 * учётной записи. Повтор с тем же ключом возвращает ту же запись auth-service и ту же карточку — без дублей;
 * так же подхватывается запись, оставшаяся после неудачной компенсации.
 *
 * Увольнение блокирует вход (учётная запись enabled = false), отмена увольнения — разблокирует. Сетевые вызовы
 * делаются вне транзакции базы; при сбое локальной части выполняется обратный вызов.
 */
@Service
public class EmployeeAdminService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeAdminService.class);

    /** Роли сотрудников приюта; VISITOR — роль посетителя, сотруднику её не назначают. */
    static final Set<String> STAFF_ROLES = Set.of("ADMIN", "VET", "CURATOR", "ADOPTION_MANAGER", "COORDINATOR");

    private static final int DEFAULT_WEEKLY_HOURS = 40;

    private final EmployeeRepository repository;
    private final PositionRepository positionRepository;
    private final SalaryRepository salaryRepository;
    private final AuthAccounts authAccounts;
    private final CurrentUser currentUser;
    private final TransactionOperations transactions;
    private final Clock clock;

    public EmployeeAdminService(EmployeeRepository repository, PositionRepository positionRepository,
                                SalaryRepository salaryRepository, AuthAccounts authAccounts, CurrentUser currentUser,
                                TransactionOperations transactions, Clock clock) {
        this.repository = repository;
        this.positionRepository = positionRepository;
        this.salaryRepository = salaryRepository;
        this.authAccounts = authAccounts;
        this.currentUser = currentUser;
        this.transactions = transactions;
        this.clock = clock;
    }

    /** Результат создания: created = false — повторный запрос, возвращена созданная ранее карточка. */
    public record CreateResult(EmployeeDetailsResponse employee, boolean created) {
    }

    public List<EmployeeAdminResponse> findAll(String search, Long positionId, Status status) {
        return repository.findAllForAdmin(trimToNull(search), positionId, status != null ? status : Status.ALL);
    }

    /** Карточка с учётной записью; если auth-service недоступен — без неё (accountUnavailable = true). */
    public EmployeeDetailsResponse findById(Long id) {
        EmployeeDetailsResponse employee = repository.findDetails(id).orElseThrow(() -> NotFoundException.employee(id));
        if (employee.userId() == null) {
            return employee;
        }
        try {
            AuthUser user = authAccounts.find(employee.userId());
            return employee.withAccount(new AccountInfo(user.id(), user.username(), user.email(), user.roles(),
                    user.enabled()), false);
        } catch (ServiceUnavailableException | NotFoundException e) {
            log.warn("Учётная запись id={} сотрудника id={} недоступна: {}", employee.userId(), id, e.getMessage());
            return employee.withAccount(null, true);
        }
    }

    // ---------- создание ----------

    /**
     * Повтор с тем же Idempotency-Key возвращает карточку, созданную первым запросом (до любых проверок:
     * иначе повтор упёрся бы в её же паспорт). Без ключа сервер берёт свой — повтор тогда даст 409 по имени.
     */
    public CreateResult create(EmployeeCreateRequest request, UUID idempotencyKey) {
        if (idempotencyKey != null) {
            var created = repository.findIdByCreationRequestId(idempotencyKey);
            if (created.isPresent()) {
                return new CreateResult(findById(created.get()), false);
            }
        }
        if (request.account() != null && request.userId() != null) {
            throw new InvalidRequestException("Укажите новую учётную запись (account) или существующую (userId), "
                    + "но не обе сразу");
        }
        UUID requestId = idempotencyKey != null ? idempotencyKey : UUID.randomUUID();
        EmployeeRecord card = validated(request.employee(), null, null);
        card.setCreationRequestId(requestId);
        if (request.userId() != null) {
            checkLinkable(request.userId());
            card.setUserId(request.userId());
            return new CreateResult(findById(transactions.execute(s -> repository.insert(card))), true);
        }
        if (request.account() == null) {
            return new CreateResult(findById(transactions.execute(s -> repository.insert(card))), true);
        }
        return createWithAccount(card, request.account(), requestId);
    }

    private CreateResult createWithAccount(EmployeeRecord card, AccountRequest account, UUID requestId) {
        Set<String> roles = staffRoles(account.roles());
        AuthUser user = authAccounts.create(requestId, account.username().trim(), account.email().trim(),
                account.password(), roles);

        // Параллельный повтор успел сохранить карточку с этой учётной записью
        var existing = repository.findIdByUserId(user.id());
        if (existing.isPresent()) {
            return new CreateResult(findById(existing.get()), false);
        }
        card.setUserId(user.id());
        try {
            Long id = transactions.execute(s -> repository.insert(card));
            return new CreateResult(findById(id), true);
        } catch (RuntimeException e) {
            compensate(user.id(), requestId, e);
            throw e;
        }
    }

    /**
     * Карточка не сохранилась — удаляем учётную запись, созданную этим запросом, чтобы не осталось записи без
     * карточки. Если карточку с ней успел сохранить параллельный повтор того же запроса, запись не трогаем.
     * Не удалось — пишем в лог: запись подхватит повторный запрос с тем же ключом.
     */
    private void compensate(Long userId, UUID requestId, RuntimeException cause) {
        if (repository.findIdByUserId(userId).isPresent()) {
            return;
        }
        try {
            authAccounts.deleteCreated(userId, requestId);
            log.info("Карточка сотрудника не сохранилась: учётная запись id={} удалена (ключ {})", userId, requestId);
        } catch (RuntimeException e) {
            log.error("Карточка сотрудника не сохранилась, и учётную запись id={} удалить не удалось (ключ {}). "
                    + "Повторите создание с тем же Idempotency-Key", userId, requestId, e);
            cause.addSuppressed(e);
        }
    }

    // ---------- изменение и привязка учётной записи ----------

    public EmployeeDetailsResponse update(Long id, EmployeeRequest request) {
        transactions.executeWithoutResult(s -> {
            EmployeeRecord current = repository.lockById(id).orElseThrow(() -> NotFoundException.employee(id));
            EmployeeRecord card = validated(request, id, current.getHireDate());
            if (current.getTerminationDate() != null && card.getHireDate().isAfter(current.getTerminationDate())) {
                throw new BusinessRuleException("Дата приёма " + card.getHireDate() + " позже даты увольнения "
                        + current.getTerminationDate());
            }
            salaryRepository.firstPaidMonth(id)
                    .map(YearMonth::from)
                    .filter(month -> card.getHireDate().isAfter(month.atEndOfMonth()))
                    .ifPresent(month -> {
                        throw new BusinessRuleException("Сотруднику начислена зарплата за " + month
                                + ": дата приёма не может быть позже этого месяца");
                    });
            repository.update(id, card);
        });
        return findById(id);
    }

    /** Привязка существующей учётной записи (например, демо-пользователя) к карточке без учётной записи. */
    public EmployeeDetailsResponse linkAccount(Long id, Long userId) {
        checkLinkable(userId);
        transactions.executeWithoutResult(s -> {
            EmployeeRecord employee = repository.lockById(id).orElseThrow(() -> NotFoundException.employee(id));
            if (userId.equals(employee.getUserId())) {
                return;
            }
            if (employee.getUserId() != null) {
                throw new BusinessRuleException("Карточка уже связана с учётной записью id=" + employee.getUserId()
                        + ". Сначала отвяжите её");
            }
            repository.updateUserId(id, userId);
        });
        return findById(id);
    }

    /** Отвязка: учётная запись остаётся в auth-service, но действия от имени сотрудника станут недоступны (409). */
    public EmployeeDetailsResponse unlinkAccount(Long id) {
        transactions.executeWithoutResult(s -> {
            EmployeeRecord employee = repository.lockById(id).orElseThrow(() -> NotFoundException.employee(id));
            if (employee.getUserId() == null) {
                throw new BusinessRuleException("Карточка не связана с учётной записью");
            }
            repository.updateUserId(id, null);
        });
        return findById(id);
    }

    /** Учётная запись существует, у неё есть роль сотрудника и она не связана с другой карточкой. */
    private void checkLinkable(Long userId) {
        AuthUser user;
        try {
            user = authAccounts.find(userId);
        } catch (NotFoundException e) {
            throw new InvalidRequestException("Учётная запись id=" + userId + " не найдена");
        }
        if (user.roles() == null || user.roles().stream().noneMatch(STAFF_ROLES::contains)) {
            throw new BusinessRuleException("У учётной записи «" + user.username() + "» нет ролей сотрудника. "
                    + "Назначьте роли (PUT /admin/users/" + userId + "/roles) и повторите");
        }
        repository.findIdByUserId(userId).ifPresent(other -> {
            throw new BusinessRuleException("Учётная запись «" + user.username() + "» уже связана с карточкой "
                    + "сотрудника id=" + other);
        });
    }

    // ---------- увольнение ----------

    /**
     * Увольнение: дата не в будущем и не раньше приёма; себя уволить нельзя; не раньше месяца, за который уже
     * начислена зарплата; уволенный не может быть организатором будущих мероприятий. Вход блокируется.
     */
    public EmployeeDetailsResponse terminate(Long id, LocalDate date) {
        EmployeeRecord employee = repository.findById(id).orElseThrow(() -> NotFoundException.employee(id));
        checkTermination(employee, date);
        Long userId = employee.getUserId();
        setAccountEnabled(userId, false);
        try {
            transactions.executeWithoutResult(s -> {
                EmployeeRecord locked = repository.lockById(id).orElseThrow(() -> NotFoundException.employee(id));
                checkTermination(locked, date);
                repository.updateTermination(id, date);
            });
        } catch (RuntimeException e) {
            restoreAccount(userId, true, e);
            throw e;
        }
        return findById(id);
    }

    /** Отмена ошибочного увольнения: дата увольнения снимается, вход разблокируется. */
    public EmployeeDetailsResponse cancelTermination(Long id) {
        EmployeeRecord employee = repository.findById(id).orElseThrow(() -> NotFoundException.employee(id));
        if (employee.getTerminationDate() == null) {
            throw new BusinessRuleException("Сотрудник «" + employee.getFullName() + "» не уволен");
        }
        Long userId = employee.getUserId();
        setAccountEnabled(userId, true);
        try {
            transactions.executeWithoutResult(s -> {
                EmployeeRecord locked = repository.lockById(id).orElseThrow(() -> NotFoundException.employee(id));
                if (locked.getTerminationDate() == null) {
                    throw new BusinessRuleException("Сотрудник «" + locked.getFullName() + "» не уволен");
                }
                repository.updateTermination(id, null);
            });
        } catch (RuntimeException e) {
            restoreAccount(userId, false, e);
            throw e;
        }
        return findById(id);
    }

    private void checkTermination(EmployeeRecord employee, LocalDate date) {
        if (employee.getTerminationDate() != null) {
            throw new BusinessRuleException("Сотрудник «" + employee.getFullName() + "» уже уволен "
                    + employee.getTerminationDate());
        }
        if (date.isAfter(today())) {
            throw new InvalidRequestException("Дата увольнения не может быть в будущем");
        }
        if (date.isBefore(employee.getHireDate())) {
            throw new BusinessRuleException("Дата увольнения " + date + " раньше даты приёма " + employee.getHireDate());
        }
        if (employee.getUserId() != null && employee.getUserId().equals(currentUser.id())) {
            throw new BusinessRuleException("Нельзя уволить самого себя");
        }
        salaryRepository.lastPaidMonth(employee.getId())
                .map(YearMonth::from)
                .filter(month -> month.isAfter(YearMonth.from(date)))
                .ifPresent(month -> {
                    throw new BusinessRuleException("Сотруднику начислена зарплата за " + month
                            + ": уволить его датой " + date + " нельзя. Сначала отмените начисление за этот месяц");
                });
        List<String> events = repository.eventsOrganizedAfter(employee.getId(), date);
        if (!events.isEmpty()) {
            throw new BusinessRuleException("Сотрудник — организатор мероприятий после даты увольнения: "
                    + String.join(", ", events) + ". Сначала назначьте другого организатора");
        }
    }

    /** Блокировка или разблокировка входа. Учётной записи уже нет в auth-service — блокировать нечего. */
    private void setAccountEnabled(Long userId, boolean enabled) {
        if (userId == null) {
            return;
        }
        try {
            authAccounts.setEnabled(userId, enabled);
        } catch (NotFoundException e) {
            log.warn("Учётная запись id={} не найдена в auth-service: статус входа не изменён", userId);
        }
    }

    private void restoreAccount(Long userId, boolean enabled, RuntimeException cause) {
        try {
            setAccountEnabled(userId, enabled);
        } catch (RuntimeException e) {
            log.error("Не удалось вернуть статус входа учётной записи id={} (enabled={})", userId, enabled, e);
            cause.addSuppressed(e);
        }
    }

    // ---------- проверки карточки ----------

    /** Проверки данных карточки. defaultHire — дата приёма, если в запросе её нет (при изменении — прежняя). */
    private EmployeeRecord validated(EmployeeRequest request, Long excludeId, LocalDate defaultHire) {
        if (positionRepository.findById(request.positionId()).isEmpty()) {
            throw new InvalidRequestException("Должность с id=" + request.positionId() + " не найдена");
        }
        LocalDate hire = request.hireDate() != null ? request.hireDate()
                : defaultHire != null ? defaultHire : today();
        if (hire.isAfter(today())) {
            throw new InvalidRequestException("Дата приёма не может быть в будущем");
        }
        if (request.birthDate() != null && !request.birthDate().isBefore(hire)) {
            throw new InvalidRequestException("Дата рождения должна быть раньше даты приёма");
        }
        String series = trimToNull(request.passportSeries());
        String number = trimToNull(request.passportNumber());
        if ((series == null) != (number == null)) {
            throw new InvalidRequestException("Паспорт: укажите и серию (4 цифры), и номер (6 цифр) или ни одного");
        }
        if (series != null && repository.passportTaken(series, number, excludeId)) {
            throw new BusinessRuleException("Паспорт " + series + " " + number + " уже указан у другого сотрудника");
        }

        EmployeeRecord card = new EmployeeRecord();
        card.setPositionId(request.positionId());
        card.setFullName(request.fullName().trim());
        card.setPhone(trimToNull(request.phone()));
        card.setEmail(trimToNull(request.email()));
        card.setAddress(trimToNull(request.address()));
        card.setBirthDate(request.birthDate());
        card.setHireDate(hire);
        card.setWeeklyHours((short) (request.weeklyHours() != null ? request.weeklyHours() : DEFAULT_WEEKLY_HOURS));
        card.setPassportSeries(series);
        card.setPassportNumber(number);
        return card;
    }

    private static Set<String> staffRoles(Set<String> roles) {
        for (String role : roles) {
            if (!STAFF_ROLES.contains(role)) {
                throw new InvalidRequestException("Роль «" + role + "» нельзя назначить сотруднику. Допустимые роли: "
                        + String.join(", ", STAFF_ROLES.stream().sorted().toList()));
            }
        }
        return roles;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
