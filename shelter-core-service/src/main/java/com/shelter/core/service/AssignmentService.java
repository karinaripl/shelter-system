package com.shelter.core.service;

import com.shelter.core.dto.AssignmentRequest;
import com.shelter.core.dto.AssignmentResponse;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.repository.AnimalRepository;
import com.shelter.core.repository.AnimalView;
import com.shelter.core.repository.AssignmentRepository;
import com.shelter.core.repository.VolunteerRepository;
import com.shelter.core.dto.VolunteerResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static com.shelter.core.service.MedicalChecks.trimToNull;

/**
 * Закрепления волонтёров за животными. Меняют ADMIN и COORDINATOR, читают ещё и CURATOR.
 * Не больше одного действующего закрепления на пару волонтёр–животное; закрепления выбывшего животного
 * закрывает выбытие (AnimalService).
 */
@Service
public class AssignmentService {

    private final AssignmentRepository repository;
    private final VolunteerRepository volunteerRepository;
    private final AnimalRepository animalRepository;
    private final Clock clock;

    public AssignmentService(AssignmentRepository repository, VolunteerRepository volunteerRepository,
                             AnimalRepository animalRepository, Clock clock) {
        this.repository = repository;
        this.volunteerRepository = volunteerRepository;
        this.animalRepository = animalRepository;
        this.clock = clock;
    }

    public AssignmentResponse findById(Long id) {
        return repository.findById(id).orElseThrow(() -> NotFoundException.assignment(id));
    }

    public List<AssignmentResponse> findByVolunteer(Long volunteerId, Boolean active) {
        volunteerRepository.findById(volunteerId).orElseThrow(() -> NotFoundException.volunteer(volunteerId));
        return repository.findByVolunteer(volunteerId, active);
    }

    public List<AssignmentResponse> findByAnimal(Long animalId, Boolean active) {
        animalRepository.findById(animalId).orElseThrow(() -> NotFoundException.animal(animalId));
        return repository.findByAnimal(animalId, active);
    }

    /**
     * Закрепление: волонтёр активен, животное не выбыло, дата начала не в будущем, не раньше поступления
     * животного и регистрации волонтёра; второе действующее закрепление на ту же пару — 409.
     * Блокировки: волонтёр (против деактивации), затем животное (против выбытия).
     */
    @Transactional
    public AssignmentResponse create(AssignmentRequest request) {
        LocalDate today = LocalDate.now(clock);
        LocalDate start = request.startDate() != null ? request.startDate() : today;
        if (start.isAfter(today)) {
            throw new InvalidRequestException("Дата начала закрепления не может быть в будущем");
        }

        VolunteerResponse volunteer = volunteerRepository.lockById(request.volunteerId())
                .orElseThrow(() -> NotFoundException.volunteer(request.volunteerId()));
        AnimalView animal = animalRepository.lockById(request.animalId())
                .orElseThrow(() -> NotFoundException.animal(request.animalId()));

        if (!Boolean.TRUE.equals(volunteer.active())) {
            throw new BusinessRuleException("Волонтёр «" + volunteer.fullName()
                    + "» неактивен: закрепить его за животным нельзя");
        }
        if (animal.departureDate() != null) {
            throw new BusinessRuleException("Животное «" + animalName(animal) + "» выбыло " + animal.departureDate()
                    + " («" + animal.statusName() + "»): закрепить за ним волонтёра нельзя");
        }
        if (start.isBefore(animal.arrivalDate())) {
            throw new BusinessRuleException("Дата начала " + start + " раньше поступления животного "
                    + animal.arrivalDate());
        }
        if (start.isBefore(volunteer.registrationDate())) {
            throw new BusinessRuleException("Дата начала " + start + " раньше регистрации волонтёра "
                    + volunteer.registrationDate());
        }
        if (repository.activeExists(volunteer.id(), animal.id())) {
            throw new BusinessRuleException("Волонтёр «" + volunteer.fullName() + "» уже закреплён за животным «"
                    + animalName(animal) + "». Сначала закройте действующее закрепление");
        }
        return findById(repository.insert(volunteer.id(), animal.id(), start, trimToNull(request.notes())));
    }

    /** Закрытие: дата окончания по умолчанию сегодня, не в будущем и не раньше начала; закрытое — 409. */
    @Transactional
    public AssignmentResponse close(Long id, LocalDate endDate) {
        AssignmentResponse assignment = repository.lockById(id).orElseThrow(() -> NotFoundException.assignment(id));
        if (!assignment.active()) {
            throw new BusinessRuleException("Закрепление уже закрыто " + assignment.endDate());
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate end = endDate != null ? endDate : today;
        if (end.isAfter(today)) {
            throw new InvalidRequestException("Дата окончания закрепления не может быть в будущем");
        }
        if (end.isBefore(assignment.startDate())) {
            throw new InvalidRequestException("Дата окончания " + end + " раньше даты начала " + assignment.startDate());
        }
        repository.close(id, end);
        return findById(id);
    }

    /** Удаление ошибочной записи о закреплении. */
    @Transactional
    public void delete(Long id) {
        repository.lockById(id).orElseThrow(() -> NotFoundException.assignment(id));
        repository.delete(id);
    }

    private static String animalName(AnimalView animal) {
        return animal.name() != null ? animal.name() : "id=" + animal.id();
    }
}
