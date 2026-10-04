package com.shelter.room.controller;

import com.shelter.room.dto.RoomExistsResponse;
import com.shelter.room.service.RoomService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/internal/rooms")
public class InternalRoomController {

    private final RoomService roomService;

    public InternalRoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping("/{id}/exists")
    public RoomExistsResponse checkExists(@PathVariable Long id) {
        return roomService.checkExists(id);
    }
}