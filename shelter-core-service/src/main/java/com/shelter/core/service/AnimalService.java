package com.shelter.core.service;

import com.shelter.core.dto.AnimalFilter;
import com.shelter.core.dto.AnimalIntakeRequest;
import com.shelter.core.dto.AnimalListItem;
import com.shelter.core.dto.AnimalResponse;
import com.shelter.core.dto.AnimalStatusCode;
import com.shelter.core.dto.AnimalUpdateRequest;
import com.shelter.core.dto.DepartureRequest;
import com.shelter.core.dto.HealthState;
import com.shelter.core.dto.PageResponse;
import com.shelter.core.dto.StatusChangeRequest;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.exception.ForbiddenActionException;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.mapper.AnimalMapper;
import com.shelter.core.repository.AnimalRepository;
import com.shelter.core.repository.AnimalView;
import com.shelter.core.repository.DepartureRepository;
import com.shelter.core.repository.MedicalCardRepository;
import com.shelter.core.security.CurrentUser;
import com.shelter.core.security.Roles;
import com.shelter.model.generated.shelter.tables.records.AnimalRecord;
import org.jooq.SortField;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.shelter.model.generated.shelter.Tables.ANIMAL;

/** Животные: поступление, описание, клетка, этапы жизненного цикла, медицинское состояние, выбытие. */
@Service
public class AnimalService {

    private static final Map<String, org.jooq.Field<?>> SORT_FIELDS = Map.of(
            "name", ANIMAL.NAME,
            "arrivalDate", ANIMAL.ARRIVAL_DATE,
            "birthDate", ANIMAL.BIRTH_DATE,
            "id", ANIMAL.ID
    );

    private final AnimalRepository animalRepository;
    private final MedicalCardRepository medicalCardRepository;
    private final DepartureRepository departureRepository;
    private final CageService cageService;
    private final DictionaryService dictionaryService;
    private final AnimalMapper animalMapper;
    private final CurrentUser currentUser;
    private final Clock clock;

    public AnimalService(AnimalRepository animalRepository,
                         MedicalCardRepository medicalCardRepository,
                         DepartureRepository departureRepository,
                         CageService cageService,
                         DictionaryService dictionaryService,
                         AnimalMapper animalMapper,
                         CurrentUser currentUser,
                         Clock clock) {
        this.animalRepository = animalRepository;
        this.medicalCardRepository = medicalCardRepository;
        this.departureRepository = departureRepository;
        this.cageService = cageService;
        this.dictionaryService = dictionaryService;
        this.animalMapper = animalMapper;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    // ---------- чтение ----------

    public PageResponse<AnimalListItem> findPage(AnimalFilter filter, int page, int size, String sort) {
        Paging.validate(page, size);
        if (filter.arrivedFrom() != null && filter.arrivedTo() != null
                && filter.arrivedFrom().isAfter(filter.arrivedTo())) {
            throw new InvalidRequestException("Начало периода поступления позже его конца");
        }
        List<AnimalListItem> content = animalRepository.findPage(filter, sortField(sort), page, size).stream()
                .map(animalMapper::toListItem)
                .toList();
        return PageResponse.of(content, page, size, animalRepository.count(filter));
    }

    public AnimalResponse findById(Long id) {
        return animalMapper.toResponse(animalRepository.findById(id).orElseThrow(() -> NotFoundException.animal(id)));
    }

    // ---------- поступление и описание ----------

    /** Поступление: животное сразу «в приюте» и занимает место в клетке; открывается медкарта. */
    @Transactional
    public AnimalResponse registerIntake(AnimalIntakeRequest request) {
        LocalDate today = LocalDate.now(clock);
        LocalDate arrivalDate = request.arrivalDate() != null ? request.arrivalDate() : today;
        if (arrivalDate.isAfter(today)) {
            throw new InvalidRequestException("Дата поступления не может быть в будущем");
        }
        checkBirthDate(request.birthDate(), arrivalDate);
        Long sourceId = dictionaryService.arrivalSource(request.arrivalSourceCode()).getId();
        cageService.reservePlace(request.cageId());

        AnimalRecord animal = new AnimalRecord();
        animal.setName(trimToNull(request.name()));
        animal.setGender(request.gender());
        animal.setBirthDate(request.birthDate());
        animal.setColor(trimToNull(request.color()));
        animal.setBreed(trimToNull(request.breed()));
        animal.setCharacter(trimToNull(request.character()));
        animal.setSpecialMarks(trimToNull(request.specialMarks()));
        animal.setStatusId(dictionaryService.statusId(AnimalStatusCode.IN_SHELTER));
        animal.setCageId(request.cageId());
        animal.setArrivalSourceId(sourceId);
        animal.setArrivalDate(arrivalDate);

        Long id = animalRepository.insert(animal);
        medicalCardRepository.createIfAbsent(id, arrivalDate);
        return findById(id);
    }

    @Transactional
    public AnimalResponse updateDescription(Long id, AnimalUpdateRequest request) {
        AnimalView animal = lock(id);
        checkBirthDate(request.birthDate(), animal.arrivalDate());
        animalRepository.updateDescription(id, trimToNull(request.name()), request.gender(), request.birthDate(),
                trimToNull(request.color()), trimToNull(request.breed()), trimToNull(request.character()),
                trimToNull(request.specialMarks()));
        return findById(id);
    }

    // ---------- клетка ----------

    @Transactional
    public AnimalResponse moveToCage(Long id, Long cageId) {
        AnimalView animal = lock(id);
        ensureNotDeparted(animal);
        if (animal.cageId() == null) {
            throw new BusinessRuleException("Животное на передержке и не занимает клетку. "
                    + "Чтобы вернуть его в приют, смените статус и укажите клетку");
        }
        if (animal.cageId().equals(cageId)) {
            throw new BusinessRuleException("Животное уже в клетке " + animal.cageNumber());
        }
        cageService.reservePlace(cageId);
        animalRepository.updateCage(id, cageId);
        return findById(id);
    }

    // ---------- этап жизненного цикла ----------

    @Transactional
    public AnimalResponse changeStatus(Long id, StatusChangeRequest request) {
        AnimalView animal = lock(id);
        ensureNotDeparted(animal);
        AnimalStatusCode from = AnimalStatusCode.valueOf(animal.statusCode());
        AnimalStatusCode to = request.status();

        if (to.isFinal()) {
            throw new BusinessRuleException("Статус «" + dictionaryService.statusName(to)
                    + "» итоговый: он ставится при оформлении выбытия (POST /animals/" + id + "/departure)");
        }
        if (from == to) {
            throw new BusinessRuleException("Животное уже в статусе «" + animal.statusName() + "»");
        }
        if (!StatusTransitions.isAllowed(from, to)) {
            throw new BusinessRuleException("Переход из статуса «" + animal.statusName() + "» в «"
                    + dictionaryService.statusName(to) + "» недопустим. Допустимые переходы — в docs/access.md");
        }
        if (StatusTransitions.requiresAdmin(from, to) && !currentUser.hasRole(Roles.ADMIN)) {
            throw new ForbiddenActionException("Бронирование ставится и снимается через заявки на пристройство; "
                    + "вручную это может сделать только администратор");
        }
        if (to == AnimalStatusCode.AVAILABLE && HealthState.QUARANTINE.name().equals(animal.healthState())) {
            throw new BusinessRuleException("Животное на карантине нельзя сделать готовым к пристройству");
        }

        Long cageId = animal.cageId();
        if (to == AnimalStatusCode.FOSTER) {
            cageId = null;
        } else if (to.requiresCage() && cageId == null) {
            if (request.cageId() == null) {
                throw new InvalidRequestException("Животное возвращается в приют с передержки: укажите клетку (cageId)");
            }
            cageService.reservePlace(request.cageId());
            cageId = request.cageId();
        }

        animalRepository.updateStatus(id, dictionaryService.statusId(to), cageId);
        return findById(id);
    }

    // ---------- медицинское состояние ----------

    @Transactional
    public AnimalResponse changeHealthState(Long id, HealthState healthState) {
        AnimalView animal = lock(id);
        ensureNotDeparted(animal);
        animalRepository.updateHealthState(id, healthState.name());
        return findById(id);
    }

    // ---------- выбытие ----------

    /**
     * Выбытие: итоговый статус, дата не в будущем и не раньше поступления, клетка освобождается,
     * животное убирается из будущих мероприятий, закрепления волонтёров закрываются.
     */
    @Transactional
    public AnimalResponse registerDeparture(Long id, DepartureRequest request) {
        AnimalStatusCode to = request.status();
        if (!to.isFinal()) {
            throw new InvalidRequestException("Статус выбытия должен быть итоговым: ADOPTED, DIED, ESCAPED или TRANSFERRED");
        }
        if (!currentUser.hasRole(Roles.ADMIN) && !currentUser.hasRole(Roles.CURATOR)
                && to != AnimalStatusCode.DIED) {
            throw new ForbiddenActionException("Ветеринар может оформить только выбытие «умер» (DIED)");
        }

        AnimalView animal = lock(id);
        ensureNotDeparted(animal);
        LocalDate date = request.departureDate();
        if (date.isAfter(LocalDate.now(clock))) {
            throw new InvalidRequestException("Дата выбытия не может быть в будущем");
        }
        if (date.isBefore(animal.arrivalDate())) {
            throw new BusinessRuleException("Дата выбытия " + date + " раньше даты поступления " + animal.arrivalDate());
        }
        AnimalStatusCode from = AnimalStatusCode.valueOf(animal.statusCode());
        if (to == AnimalStatusCode.ADOPTED && !StatusTransitions.canBeAdoptedFrom(from)) {
            throw new BusinessRuleException("Пристроить можно только животное, готовое к пристройству "
                    + "или забронированное. Сейчас: «" + animal.statusName() + "»");
        }

        animalRepository.depart(id, dictionaryService.statusId(to), date, trimToNull(request.note()));
        departureRepository.removeFromEventsAfter(id, date);
        departureRepository.closeAssignments(id, date);
        return findById(id);
    }

    // ---------- вспомогательное ----------

    private AnimalView lock(Long id) {
        return animalRepository.lockById(id).orElseThrow(() -> NotFoundException.animal(id));
    }

    private static void ensureNotDeparted(AnimalView animal) {
        if (animal.departureDate() != null) {
            throw new BusinessRuleException("Животное выбыло " + animal.departureDate()
                    + " («" + animal.statusName() + "»): изменить это нельзя");
        }
    }

    private static void checkBirthDate(LocalDate birthDate, LocalDate arrivalDate) {
        if (birthDate != null && birthDate.isAfter(arrivalDate)) {
            throw new InvalidRequestException("Дата рождения " + birthDate + " позже даты поступления " + arrivalDate);
        }
    }

    /** Сортировка «поле,направление», например «arrivalDate,desc». По умолчанию — новые поступления первыми. */
    static SortField<?> sortField(String sort) {
        if (sort == null || sort.isBlank()) {
            return ANIMAL.ARRIVAL_DATE.desc();
        }
        String[] parts = sort.split(",");
        org.jooq.Field<?> field = SORT_FIELDS.get(parts[0].trim());
        if (field == null || parts.length > 2) {
            throw new InvalidRequestException("Сортировка: поле name, arrivalDate, birthDate или id, "
                    + "направление asc или desc (например, arrivalDate,desc)");
        }
        String direction = parts.length == 2 ? parts[1].trim().toLowerCase() : "asc";
        return switch (direction) {
            case "asc" -> field.asc().nullsLast();
            case "desc" -> field.desc().nullsLast();
            default -> throw new InvalidRequestException("Направление сортировки: asc или desc");
        };
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
