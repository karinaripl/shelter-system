package com.shelter.room.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/room")
public class HelloController {
    @Value("${spring.application.name}")
    private String serviceName;

    @GetMapping("/")
    public String hello(){
        return "Hello my name: " + serviceName + " v1";
    }

}
