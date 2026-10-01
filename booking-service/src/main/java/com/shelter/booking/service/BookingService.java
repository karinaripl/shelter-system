package com.shelter.booking.service;

import com.shelter.booking.client.NotificationClient;
import com.shelter.booking.client.RoomClient;
import com.shelter.booking.dto.BookingRequest;
import com.shelter.booking.dto.BookingResponse;
import com.shelter.booking.dto.NotificationRequest;
import com.shelter.booking.dto.RoomExistsResponse;
import com.shelter.booking.exception.BookingNotFoundException;
import com.shelter.booking.exception.CapacityExceededException;
import com.shelter.booking.exception.RoomNotFoundException;
import com.shelter.booking.mapper.BookingMapper;
import com.shelter.booking.repository.BookingRepository;
import com.shelter.model.generated.booking.tables.records.BookingRecord;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;
    private final RoomClient roomClient;
    private final NotificationClient notificationClient;

    public BookingService(BookingRepository bookingRepository,
                          BookingMapper bookingMapper,
                          RoomClient roomClient,
                          NotificationClient notificationClient) {
        this.bookingRepository = bookingRepository;
        this.bookingMapper = bookingMapper;
        this.roomClient = roomClient;
        this.notificationClient = notificationClient;
    }

    public BookingResponse create(Long userId, BookingRequest request) {
        RoomExistsResponse room;
        try {
            room = roomClient.checkExists(request.roomId());
        } catch (FeignException.NotFound e) {
            throw new RoomNotFoundException(request.roomId());
        }

        if (!room.exists()) {
            throw new RoomNotFoundException(request.roomId());
        }

        if (request.attendeesCount() > room.capacity()) {
            throw new CapacityExceededException(request.attendeesCount(), room.capacity());
        }

        BookingRecord record = bookingRepository.insert(
                userId, request.roomId(), request.startTime(), request.endTime());

        notifySafely(userId, "BOOKING_CREATED",
                "Ваша бронь на " + request.startTime() + " создана и ожидает подтверждения");

        return bookingMapper.toResponse(record);
    }

    public List<BookingResponse> findMyBookings(Long userId) {
        return bookingRepository.findByUserId(userId).stream()
                .map(bookingMapper::toResponse)
                .toList();
    }

    public BookingResponse findById(Long id, Long userId, boolean isAdmin) {
        BookingRecord record = bookingRepository.findById(id);
        if (record == null || (!isAdmin && !record.getUserId().equals(userId))) {
            throw new BookingNotFoundException(id);
        }
        return bookingMapper.toResponse(record);
    }

    public void cancel(Long id, Long userId, boolean isAdmin) {
        BookingRecord record = bookingRepository.findById(id);
        if (record == null || (!isAdmin && !record.getUserId().equals(userId))) {
            throw new BookingNotFoundException(id);
        }

        bookingRepository.cancel(id);

        notifySafely(record.getUserId(), "BOOKING_CANCELLED",
                "Ваша бронь на " + record.getStartTime() + " отменена");
    }

    private void notifySafely(Long userId, String type, String message) {
        try {
            notificationClient.send(new NotificationRequest(userId, type, message));
        } catch (Exception e) {
            log.warn("Не удалось отправить уведомление userId={}, type={}: {}", userId, type, e.getMessage());
        }
    }
}