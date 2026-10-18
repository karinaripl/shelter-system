package com.shelter.core.service;

import com.shelter.core.dto.CageDetailsResponse;
import com.shelter.core.dto.CageRequest;
import com.shelter.core.dto.CageResponse;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.mapper.AnimalMapper;
import com.shelter.core.mapper.CageMapper;
import com.shelter.core.repository.AnimalRepository;
import com.shelter.core.repository.CageRepository;
import com.shelter.core.repository.CageView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CageService {

    private final CageRepository cageRepository;
    private final AnimalRepository animalRepository;
    private final CageMapper cageMapper;
    private final AnimalMapper animalMapper;

    public CageService(CageRepository cageRepository, AnimalRepository animalRepository,
                       CageMapper cageMapper, AnimalMapper animalMapper) {
        this.cageRepository = cageRepository;
        this.animalRepository = animalRepository;
        this.cageMapper = cageMapper;
        this.animalMapper = animalMapper;
    }

    public List<CageResponse> findAll() {
        return cageRepository.findAll().stream().map(cageMapper::toResponse).toList();
    }

    public CageDetailsResponse findById(Long id) {
        CageView cage = cageRepository.findById(id).orElseThrow(() -> NotFoundException.cage(id));
        return cageMapper.toDetails(cage,
                animalRepository.findByCage(id).stream().map(animalMapper::toBrief).toList());
    }

    @Transactional
    public CageResponse create(CageRequest request) {
        String number = request.number().trim();
        if (cageRepository.existsByNumber(number, null)) {
            throw new BusinessRuleException("Клетка с номером " + number + " уже есть");
        }
        Long id = cageRepository.insert(number, request.capacity(), blankToNull(request.cageType()));
        return cageMapper.toResponse(cageRepository.findById(id).orElseThrow());
    }

    @Transactional
    public CageResponse update(Long id, CageRequest request) {
        CageView cage = cageRepository.lockById(id).orElseThrow(() -> NotFoundException.cage(id));
        String number = request.number().trim();
        if (cageRepository.existsByNumber(number, id)) {
            throw new BusinessRuleException("Клетка с номером " + number + " уже есть");
        }
        if (request.capacity() < cage.occupied()) {
            throw new BusinessRuleException("Нельзя уменьшить вместимость клетки " + cage.number() + " до "
                    + request.capacity() + ": в ней сейчас " + cage.occupied() + " животн.");
        }
        cageRepository.update(id, number, request.capacity(), blankToNull(request.cageType()));
        return cageMapper.toResponse(cageRepository.findById(id).orElseThrow());
    }

    @Transactional
    public void delete(Long id) {
        CageView cage = cageRepository.lockById(id).orElseThrow(() -> NotFoundException.cage(id));
        if (cage.occupied() > 0) {
            throw new BusinessRuleException("Нельзя удалить клетку " + cage.number()
                    + ": в ней " + cage.occupied() + " животн. Сначала переведите их в другие клетки");
        }
        cageRepository.delete(id);
    }

    /**
     * Блокирует клетку и проверяет, что в ней есть место. Используется при поступлении,
     * переводе и возвращении с передержки. Клетка из тела запроса, которой нет, — ошибка данных (400).
     */
    public CageView reservePlace(Long cageId) {
        CageView cage = cageRepository.lockById(cageId)
                .orElseThrow(() -> new InvalidRequestException("Клетка с id=" + cageId + " не найдена"));
        if (cage.occupied() >= cage.capacity()) {
            throw new BusinessRuleException("Клетка " + cage.number() + " заполнена: занято "
                    + cage.occupied() + " из " + cage.capacity());
        }
        return cage;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
