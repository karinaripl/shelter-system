package com.shelter.room.service;

import com.shelter.model.generated.room.tables.records.RoomsRecord;
import com.shelter.room.dto.RoomExistsResponse;
import com.shelter.room.dto.RoomRequest;
import com.shelter.room.dto.RoomResponse;
import com.shelter.room.exception.RoomNotFoundException;
import com.shelter.room.mapper.RoomMapper;
import com.shelter.room.repository.RoomRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomMapper roomMapper;

    public RoomService(RoomRepository roomRepository, RoomMapper roomMapper) {
        this.roomRepository = roomRepository;
        this.roomMapper = roomMapper;
    }

    public RoomResponse create(RoomRequest request) {
        RoomsRecord record = roomRepository.insert(
                request.name(), request.capacity(), request.description(), request.location());
        return roomMapper.toResponse(record);
    }

    public List<RoomResponse> findAll() {
        return roomRepository.findAllActive().stream()
                .map(roomMapper::toResponse)
                .toList();
    }

    public RoomResponse findById(Long id) {
        RoomsRecord record = roomRepository.findById(id);
        if (record == null) {
            throw new RoomNotFoundException(id);
        }
        return roomMapper.toResponse(record);
    }

    public RoomResponse update(Long id, RoomRequest request) {
        int updated = roomRepository.update(
                id, request.name(), request.capacity(), request.description(), request.location());
        if (updated == 0) {
            throw new RoomNotFoundException(id);
        }
        return findById(id);
    }

    public void delete(Long id) {
        int updated = roomRepository.deactivate(id);
        if (updated == 0) {
            throw new RoomNotFoundException(id);
        }
    }

    public RoomExistsResponse checkExists(Long id) {
        RoomsRecord record = roomRepository.findActiveById(id);
        if (record == null) {
            return new RoomExistsResponse(false, id, null);
        }
        return new RoomExistsResponse(true, id, record.getCapacity());
    }
}