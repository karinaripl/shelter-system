package com.shelter.core.service;

import com.shelter.core.dto.EventEffectivenessItem;
import com.shelter.core.dto.EventEffectivenessReport;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.repository.EventRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Отчёт об эффективности мероприятий (ADMIN, ADOPTION_MANAGER, COORDINATOR): по каждому мероприятию периода —
 * участники и доля пристроенных впоследствии, плюс итог. Будущие мероприятия не учитываются, даже если конец
 * периода в будущем. Период по умолчанию — последние 365 дней.
 */
@Service
public class EventReportService {

    static final int DEFAULT_PERIOD_DAYS = 365;

    private final EventRepository repository;
    private final Clock clock;

    public EventReportService(EventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public EventEffectivenessReport effectiveness(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock);
        LocalDate end = to == null || to.isAfter(today) ? today : to;
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_PERIOD_DAYS);
        if (to != null && from != null && from.isAfter(to)) {
            throw new InvalidRequestException("Начало периода (from) позже конца (to)");
        }

        List<EventEffectivenessItem> events = start.isAfter(end) ? List.of() : repository.effectiveness(start, end);
        int participants = events.stream().mapToInt(EventEffectivenessItem::participants).sum();
        int adopted = events.stream().mapToInt(EventEffectivenessItem::adopted).sum();
        EventEffectivenessReport.Total total = new EventEffectivenessReport.Total(events.size(), participants, adopted,
                EventRepository.percent(adopted, participants));
        return new EventEffectivenessReport(start, end, events, total);
    }
}
