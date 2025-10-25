package com.example.traveler.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.traveler.dto.LocationDTO;
import com.example.traveler.dto.UpdateLocationRequest;
import com.example.traveler.service.LocationService;

import java.util.UUID;

@RestController
@RequestMapping("/api/locations") // Базовий шлях [cite: 50, 51]
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @PutMapping("/{id}") // [cite: 50]
    public ResponseEntity<LocationDTO> updateLocation(@PathVariable UUID id,
                                                      @Valid @RequestBody UpdateLocationRequest request) {
        LocationDTO updatedLocation = locationService.updateLocation(id, request);
        return ResponseEntity.ok(updatedLocation); // 200 OK
    }

    @DeleteMapping("/{id}") // [cite: 51]
    @ResponseStatus(HttpStatus.NO_CONTENT) // 204 No Content
    public void deleteLocation(@PathVariable UUID id) {
        locationService.deleteLocation(id);
    }
}