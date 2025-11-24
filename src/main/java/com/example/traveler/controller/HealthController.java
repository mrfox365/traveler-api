package com.example.traveler.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        // Повертає статус 200 OK і просте повідомлення
        return ResponseEntity.ok("UP");
    }
}