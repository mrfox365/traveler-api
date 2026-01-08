package com.example.traveler.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.traveler.dto.*;
import com.example.traveler.service.LocationService;
import com.example.traveler.service.TravelPlanService;

import java.util.UUID;

@RestController
@RequestMapping("/api/travel-plans")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class TravelPlanController {

    private final TravelPlanService travelPlanService;
    private final LocationService locationService; // Потрібен для додавання локацій

    @GetMapping // [cite: 47]
    public Page<PlanSummaryResponse> listPlans(Pageable pageable) {
        return travelPlanService.getAllPlans(pageable);
    }

    @PostMapping // [cite: 47]
    public ResponseEntity<PlanResponse> createPlan(@Valid @RequestBody CreatePlanRequest request) {
        PlanResponse createdPlan = travelPlanService.createPlan(request);
        // Повертаємо 201 Created
        return ResponseEntity.status(HttpStatus.CREATED).body(createdPlan);
    }

    @GetMapping("/{id}") // [cite: 48]
    public ResponseEntity<PlanResponse> getPlan(@PathVariable UUID id) {
        PlanResponse plan = travelPlanService.getPlanById(id);
        return ResponseEntity.ok(plan); // 200 OK
    }

    @PutMapping("/{id}") // [cite: 48]
    public ResponseEntity<PlanResponse> updatePlan(@PathVariable UUID id,
                                                   @Valid @RequestBody UpdatePlanRequest request) {
        PlanResponse updatedPlan = travelPlanService.updatePlan(id, request);
        return ResponseEntity.ok(updatedPlan); // 200 OK
    }

    @DeleteMapping("/{id}") // [cite: 48]
    @ResponseStatus(HttpStatus.NO_CONTENT) // 204 No Content
    public void deletePlan(@PathVariable UUID id) {
        travelPlanService.deletePlan(id);
    }

    @PostMapping("/{id}/locations") // [cite: 50]
    public ResponseEntity<LocationDTO> addLocation(@PathVariable UUID id,
                                                   @Valid @RequestBody CreateLocationRequest request) {
        LocationDTO newLocation = locationService.addLocationToPlan(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(newLocation); // 201 Created
    }
}