package com.shelter.core.service;

import com.shelter.core.dto.AnimalStatusCode;
import com.shelter.core.dto.AnimalStatusResponse;
import com.shelter.core.dto.DictionaryItem;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.repository.DictionaryRepository;
import com.shelter.model.generated.shelter.tables.records.ArrivalSourceRecord;
import org.springframework.stereotype.Service;

import java.util.List;

/** Справочники для форм. Значения меняются только миграциями: их коды используются в логике. */
@Service
public class DictionaryService {

    private final DictionaryRepository dictionaryRepository;

    public DictionaryService(DictionaryRepository dictionaryRepository) {
        this.dictionaryRepository = dictionaryRepository;
    }

    public List<AnimalStatusResponse> findAllStatuses() {
        return dictionaryRepository.findAllStatuses().stream()
                .map(s -> new AnimalStatusResponse(s.getCode(), s.getName(), Boolean.TRUE.equals(s.getIsFinal())))
                .toList();
    }

    public List<DictionaryItem> findAllArrivalSources() {
        return dictionaryRepository.findAllArrivalSources().stream()
                .map(s -> new DictionaryItem(s.getCode(), s.getName()))
                .toList();
    }

    public Long statusId(AnimalStatusCode code) {
        return dictionaryRepository.findStatusByCode(code.name())
                .orElseThrow(() -> new IllegalStateException("В справочнике нет статуса " + code))
                .getId();
    }

    /** Название статуса для сообщений («готов к пристройству» вместо AVAILABLE). */
    public String statusName(AnimalStatusCode code) {
        return dictionaryRepository.findStatusByCode(code.name())
                .map(s -> s.getName())
                .orElse(code.name());
    }

    public ArrivalSourceRecord arrivalSource(String code) {
        return dictionaryRepository.findArrivalSourceByCode(code)
                .orElseThrow(() -> new InvalidRequestException("Неизвестный источник поступления: " + code
                        + ". Список — GET /arrival-sources"));
    }
}
