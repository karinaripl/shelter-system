package com.shelter.core.service;

import com.shelter.core.dto.PositionRequest;
import com.shelter.core.dto.PositionResponse;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.repository.PositionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Справочник должностей (только ADMIN): название уникально без учёта регистра, оклад ≥ 0. Новый оклад действует
 * на следующие начисления зарплаты, уже начисленные суммы не меняются. Должность с сотрудниками не удаляется.
 */
@Service
public class PositionService {

    private final PositionRepository repository;

    public PositionService(PositionRepository repository) {
        this.repository = repository;
    }

    public List<PositionResponse> findAll() {
        return repository.findAll();
    }

    public PositionResponse findById(Long id) {
        return repository.findById(id).orElseThrow(() -> NotFoundException.position(id));
    }

    @Transactional
    public PositionResponse create(PositionRequest request) {
        String name = uniqueName(request.name(), null);
        return findById(repository.insert(name, Money.of(request.baseSalary())));
    }

    @Transactional
    public PositionResponse update(Long id, PositionRequest request) {
        if (!repository.lockById(id)) {
            throw NotFoundException.position(id);
        }
        repository.update(id, uniqueName(request.name(), id), Money.of(request.baseSalary()));
        return findById(id);
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.lockById(id)) {
            throw NotFoundException.position(id);
        }
        PositionResponse position = findById(id);
        if (position.employees() > 0) {
            throw new BusinessRuleException("Нельзя удалить должность «" + position.name() + "»: на ней числятся "
                    + "сотрудники (" + position.employees() + "), в том числе уволенные");
        }
        repository.delete(id);
    }

    private String uniqueName(String raw, Long excludeId) {
        String name = raw.trim();
        if (repository.nameTaken(name, excludeId)) {
            throw new BusinessRuleException("Должность «" + name + "» уже есть");
        }
        return name;
    }
}
