package com.shelter.core.service;

import com.shelter.core.dto.BenefactorRequest;
import com.shelter.core.dto.BenefactorResponse;
import com.shelter.core.dto.PageResponse;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.repository.BenefactorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.shelter.core.service.MedicalChecks.trimToNull;

/**
 * Благотворители с контактами (только ADMIN). Благотворителя с пожертвованиями удалить нельзя — это история
 * поступлений (409). Куратор видит только имена (GET /benefactors).
 */
@Service
public class BenefactorService {

    private final BenefactorRepository repository;

    public BenefactorService(BenefactorRepository repository) {
        this.repository = repository;
    }

    public PageResponse<BenefactorResponse> findPage(String name, int page, int size) {
        Paging.validate(page, size);
        String filter = trimToNull(name);
        return PageResponse.of(repository.findPage(filter, page, size), page, size, repository.count(filter));
    }

    public BenefactorResponse findById(Long id) {
        return repository.findById(id).orElseThrow(() -> NotFoundException.benefactor(id));
    }

    @Transactional
    public BenefactorResponse create(BenefactorRequest request) {
        return findById(repository.insert(normalized(request)));
    }

    @Transactional
    public BenefactorResponse update(Long id, BenefactorRequest request) {
        if (!repository.lockById(id)) {
            throw NotFoundException.benefactor(id);
        }
        repository.update(id, normalized(request));
        return findById(id);
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.lockById(id)) {
            throw NotFoundException.benefactor(id);
        }
        BenefactorResponse benefactor = findById(id);
        if (benefactor.donations() > 0) {
            throw new BusinessRuleException("Нельзя удалить благотворителя «" + benefactor.fullName()
                    + "»: у него есть пожертвования (" + benefactor.donations() + ")");
        }
        repository.delete(id);
    }

    private static BenefactorRequest normalized(BenefactorRequest r) {
        return new BenefactorRequest(r.fullName().trim(), trimToNull(r.phone()), trimToNull(r.email()),
                trimToNull(r.notes()));
    }
}
