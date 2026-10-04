package com.shelter.notification.controller;

import com.shelter.notification.dto.NotificationRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/notifications")
public class InternalNotificationController {

    private static final Logger log = LoggerFactory.getLogger(InternalNotificationController.class);

    @PostMapping
    public ResponseEntity<Void> accept(@Valid @RequestBody NotificationRequest request) {
        log.info("Уведомление принято: userId={}, type={}, message={}",
                request.userId(), request.type(), request.message());
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}