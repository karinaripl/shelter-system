package com.shelter.core.service;

import com.shelter.core.dto.PageResponse;
import com.shelter.core.dto.VolunteerRequest;
import com.shelter.core.dto.VolunteerResponse;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.repository.VolunteerRepository;
import com.shelter.model.generated.shelter.tables.records.VolunteerRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static com.shelter.core.service.MedicalChecks.trimToNull;

/**
 * Волонтёры: анкета, поиск, признак «активен». Анкету меняют ADMIN и COORDINATOR; что видит куратор
 * (только имя и контакты), решает контроллер. Нарушение правила — 409, ошибка данных — 400.
 */
@Service
public class VolunteerService {

    private final VolunteerRepository repository;
    private final Clock clock;

    public VolunteerService(VolunteerRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public PageResponse<VolunteerResponse> findPage(String name, String skills, boolean includeInactive,
                                                    int page, int size) {
        Paging.validate(page, size);
        String nameFilter = trimToNull(name);
        String skillsFilter = trimToNull(skills);
        List<VolunteerResponse> content = repository.findPage(nameFilter, skillsFilter, includeInactive, page, size);
        return PageResponse.of(content, page, size, repository.count(nameFilter, skillsFilter, includeInactive));
    }

    public VolunteerResponse findById(Long id) {
        return repository.findById(id).orElseThrow(() -> NotFoundException.volunteer(id));
    }

    @Transactional
    public VolunteerResponse create(VolunteerRequest request) {
        LocalDate registration = request.registrationDate() != null ? request.registrationDate() : today();
        VolunteerRecord volunteer = validated(request, registration, null);
        return findById(repository.insert(volunteer));
    }

    @Transactional
    public VolunteerResponse update(Long id, VolunteerRequest request) {
        VolunteerResponse current = repository.lockById(id).orElseThrow(() -> NotFoundException.volunteer(id));
        LocalDate registration = request.registrationDate() != null
                ? request.registrationDate() : current.registrationDate();
        VolunteerRecord volunteer = validated(request, registration, id);
        repository.firstAssignmentStart(id)
                .filter(registration::isAfter)
                .ifPresent(start -> {
                    throw new BusinessRuleException("Дата регистрации " + registration
                            + " позже начала закрепления волонтёра " + start);
                });
        repository.update(id, volunteer);
        return findById(id);
    }

    /**
     * Активен ли волонтёр. Неактивного нельзя закрепить за животным; деактивировать волонтёра
     * с действующими закреплениями нельзя — сначала их нужно закрыть.
     */
    @Transactional
    public VolunteerResponse setActive(Long id, boolean active) {
        VolunteerResponse volunteer = repository.lockById(id).orElseThrow(() -> NotFoundException.volunteer(id));
        if (!active) {
            int current = repository.countAssignments(id, true);
            if (current > 0) {
                throw new BusinessRuleException("Нельзя сделать неактивным волонтёра «" + volunteer.fullName()
                        + "»: у него действующие закрепления (" + current + "). Сначала закройте их");
            }
        }
        repository.updateActive(id, active);
        return findById(id);
    }

    /** Волонтёра с закреплениями (в том числе закрытыми) удалить нельзя: это история, его можно сделать неактивным. */
    @Transactional
    public void delete(Long id) {
        VolunteerResponse volunteer = repository.lockById(id).orElseThrow(() -> NotFoundException.volunteer(id));
        int assignments = repository.countAssignments(id, false);
        if (assignments > 0) {
            throw new BusinessRuleException("Нельзя удалить волонтёра «" + volunteer.fullName()
                    + "»: у него есть закрепления (" + assignments + "). Сделайте его неактивным");
        }
        repository.delete(id);
    }

    // ---------- вспомогательное ----------

    private VolunteerRecord validated(VolunteerRequest request, LocalDate registration, Long excludeId) {
        if (registration.isAfter(today())) {
            throw new InvalidRequestException("Дата регистрации не может быть в будущем");
        }
        if (request.birthDate() != null && !request.birthDate().isBefore(registration)) {
            throw new InvalidRequestException("Дата рождения должна быть раньше даты регистрации");
        }
        String series = trimToNull(request.passportSeries());
        String number = trimToNull(request.passportNumber());
        if ((series == null) != (number == null)) {
            throw new InvalidRequestException("Паспорт: укажите и серию (4 цифры), и номер (6 цифр) или ни одного");
        }
        if (series != null && repository.passportTaken(series, number, excludeId)) {
            throw new BusinessRuleException("Паспорт " + series + " " + number + " уже указан у другого волонтёра");
        }

        VolunteerRecord volunteer = new VolunteerRecord();
        volunteer.setFullName(request.fullName().trim());
        volunteer.setPhone(trimToNull(request.phone()));
        volunteer.setEmail(trimToNull(request.email()));
        volunteer.setAddress(trimToNull(request.address()));
        volunteer.setBirthDate(request.birthDate());
        volunteer.setRegistrationDate(registration);
        volunteer.setSkills(trimToNull(request.skills()));
        volunteer.setPassportSeries(series);
        volunteer.setPassportNumber(number);
        return volunteer;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
