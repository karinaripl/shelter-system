package com.shelter.core.service;

import com.shelter.core.dto.AnimalEventItem;
import com.shelter.core.dto.EmployeeBrief;
import com.shelter.core.dto.EventDetailsResponse;
import com.shelter.core.dto.EventRequest;
import com.shelter.core.dto.EventResponse;
import com.shelter.core.dto.EventUpdateResult;
import com.shelter.core.dto.PageResponse;
import com.shelter.core.dto.PublicEventItem;
import com.shelter.core.dto.RemovedParticipant;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.repository.AnimalRepository;
import com.shelter.core.repository.AnimalView;
import com.shelter.core.repository.EmployeeRepository;
import com.shelter.core.repository.EventRepository;
import com.shelter.core.repository.EventView;
import com.shelter.core.security.CurrentUser;
import com.shelter.core.security.Roles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static com.shelter.core.service.MedicalChecks.trimToNull;

/**
 * Мероприятия и участие животных. Меняют ADMIN, ADOPTION_MANAGER, COORDINATOR; читают все сотрудники.
 * Список участников можно составить заранее (до дня мероприятия включительно); в прошедшее мероприятие
 * участников добавляет и убирает только ADMIN (исправление ошибок).
 */
@Service
public class EventService {

    private static final int PUBLIC_MAX_SIZE = 50;

    private final EventRepository repository;
    private final AnimalRepository animalRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeService employeeService;
    private final CurrentUser currentUser;
    private final Clock clock;

    public EventService(EventRepository repository, AnimalRepository animalRepository,
                        EmployeeRepository employeeRepository, EmployeeService employeeService,
                        CurrentUser currentUser, Clock clock) {
        this.repository = repository;
        this.animalRepository = animalRepository;
        this.employeeRepository = employeeRepository;
        this.employeeService = employeeService;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    // ---------- чтение ----------

    public PageResponse<EventResponse> findPage(LocalDate from, LocalDate to, int page, int size) {
        Paging.validate(page, size);
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException("Начало периода (from) позже конца (to)");
        }
        List<EventResponse> content = repository.findPage(from, to, page, size).stream()
                .map(EventService::toResponse)
                .toList();
        return PageResponse.of(content, page, size, repository.count(from, to));
    }

    public EventDetailsResponse findById(Long id) {
        return details(repository.findById(id).orElseThrow(() -> NotFoundException.event(id)));
    }

    public List<AnimalEventItem> findByAnimal(Long animalId) {
        animalRepository.findById(animalId).orElseThrow(() -> NotFoundException.animal(animalId));
        return repository.findByAnimal(animalId);
    }

    /** Анонсы для публичной страницы: мероприятия с сегодняшнего дня, без организатора и заметок. */
    public PageResponse<PublicEventItem> findUpcoming(int page, int size) {
        int pageSize = Math.min(size, PUBLIC_MAX_SIZE);
        Paging.validate(page, pageSize);
        LocalDate today = today();
        return PageResponse.of(repository.findUpcoming(today, page, pageSize), page, pageSize,
                repository.countUpcoming(today));
    }

    // ---------- мероприятия ----------

    /** Организатор по умолчанию — текущий пользователь; без связанной карточки сотрудника — 409. */
    @Transactional
    public EventDetailsResponse create(EventRequest request) {
        Long organizerId = request.organizerId() != null
                ? checkedOrganizer(request.organizerId())
                : employeeService.currentEmployeeId();
        Long id = repository.insert(request.name().trim(), request.eventDate(), trimToNull(request.location()),
                organizerId, trimToNull(request.notes()));
        return findById(id);
    }

    /**
     * Изменение мероприятия. При переносе даты из него убираются животные, выбывшие раньше новой даты
     * (они перечислены в ответе); раньше поступления участника перенести нельзя. Организатор не указан — прежний.
     */
    @Transactional
    public EventUpdateResult update(Long id, EventRequest request) {
        EventView event = lock(id);
        Long organizerId = request.organizerId() == null || request.organizerId().equals(event.organizerId())
                ? event.organizerId()
                : checkedOrganizer(request.organizerId());

        LocalDate newDate = request.eventDate();
        List<RemovedParticipant> removed = List.of();
        if (!newDate.equals(event.eventDate())) {
            List<String> notArrived = repository.findArrivedAfter(id, newDate);
            if (!notArrived.isEmpty()) {
                throw new BusinessRuleException("Нельзя перенести мероприятие на " + newDate
                        + ": животные поступили в приют позже — " + String.join(", ", notArrived));
            }
            removed = repository.findDepartedBefore(id, newDate);
            removed.forEach(animal -> repository.removeParticipant(id, animal.animalId()));
        }
        repository.update(id, request.name().trim(), newDate, trimToNull(request.location()), organizerId,
                trimToNull(request.notes()));
        return new EventUpdateResult(findById(id), removed);
    }

    /** Прошедшее мероприятие с участниками не удаляется: это история для отчёта об эффективности. */
    @Transactional
    public void delete(Long id) {
        EventView event = lock(id);
        if (event.eventDate().isBefore(today()) && event.participantCount() > 0) {
            throw new BusinessRuleException("Нельзя удалить прошедшее мероприятие «" + event.name()
                    + "» с участниками: оно учитывается в отчёте об эффективности");
        }
        repository.delete(id);
    }

    // ---------- участие ----------

    /**
     * Добавление животного: в любой день до мероприятия включительно (в прошедшее — только ADMIN);
     * животное не выбыло раньше даты мероприятия и поступило не позже неё; повторно — 409.
     * Блокировки: мероприятие (против переноса), затем животное (против выбытия).
     */
    @Transactional
    public EventDetailsResponse addParticipant(Long eventId, Long animalId) {
        EventView event = lock(eventId);
        ensureCanChangeParticipants(event, "добавить");
        AnimalView animal = animalRepository.lockById(animalId).orElseThrow(() -> NotFoundException.animal(animalId));
        if (animal.departureDate() != null && animal.departureDate().isBefore(event.eventDate())) {
            throw new BusinessRuleException("Животное «" + animalName(animal) + "» выбыло " + animal.departureDate()
                    + " («" + animal.statusName() + "»), раньше мероприятия " + event.eventDate());
        }
        if (animal.arrivalDate().isAfter(event.eventDate())) {
            throw new BusinessRuleException("Животное «" + animalName(animal) + "» поступило " + animal.arrivalDate()
                    + ", позже мероприятия " + event.eventDate());
        }
        if (repository.participates(eventId, animalId)) {
            throw new BusinessRuleException("Животное «" + animalName(animal) + "» уже участвует в мероприятии «"
                    + event.name() + "»");
        }
        repository.addParticipant(eventId, animalId);
        return findById(eventId);
    }

    /** Состояние после мероприятия — только с даты мероприятия. */
    @Transactional
    public EventDetailsResponse updateCondition(Long eventId, Long animalId, String conditionAfter) {
        EventView event = lock(eventId);
        ensureParticipates(eventId, animalId);
        if (event.eventDate().isAfter(today())) {
            throw new BusinessRuleException("Мероприятие «" + event.name() + "» ещё не прошло (" + event.eventDate()
                    + "): состояние после него указать нельзя");
        }
        repository.updateCondition(eventId, animalId, trimToNull(conditionAfter));
        return findById(eventId);
    }

    /** Убрать животное из мероприятия; из прошедшего — только ADMIN. */
    @Transactional
    public void removeParticipant(Long eventId, Long animalId) {
        EventView event = lock(eventId);
        ensureParticipates(eventId, animalId);
        ensureCanChangeParticipants(event, "убрать");
        repository.removeParticipant(eventId, animalId);
    }

    // ---------- вспомогательное ----------

    private void ensureCanChangeParticipants(EventView event, String action) {
        if (event.eventDate().isBefore(today()) && !currentUser.hasRole(Roles.ADMIN)) {
            throw new BusinessRuleException("Мероприятие «" + event.name() + "» уже прошло (" + event.eventDate()
                    + "): " + action + " участника может только администратор");
        }
    }

    private void ensureParticipates(Long eventId, Long animalId) {
        if (!repository.participates(eventId, animalId)) {
            throw new NotFoundException("Животное с id=" + animalId + " не участвует в мероприятии с id=" + eventId);
        }
    }

    /** Явно указанный организатор: сотрудник существует (иначе 400) и работает (иначе 409). */
    private Long checkedOrganizer(Long employeeId) {
        boolean working = employeeRepository.findWorking(employeeId)
                .orElseThrow(() -> new InvalidRequestException("Сотрудник с id=" + employeeId + " не найден"));
        if (!working) {
            throw new BusinessRuleException("Сотрудник с id=" + employeeId + " уволен: он не может быть организатором");
        }
        return employeeId;
    }

    private EventView lock(Long id) {
        return repository.lockById(id).orElseThrow(() -> NotFoundException.event(id));
    }

    private EventDetailsResponse details(EventView event) {
        return new EventDetailsResponse(event.id(), event.name(), event.eventDate(), event.location(),
                organizer(event), event.notes(), repository.findParticipants(event.id()));
    }

    private static EventResponse toResponse(EventView event) {
        return new EventResponse(event.id(), event.name(), event.eventDate(), event.location(), organizer(event),
                event.notes(), event.participantCount());
    }

    private static EmployeeBrief organizer(EventView event) {
        return new EmployeeBrief(event.organizerId(), event.organizerName(), event.organizerPosition());
    }

    private static String animalName(AnimalView animal) {
        return animal.name() != null ? animal.name() : "id=" + animal.id();
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
