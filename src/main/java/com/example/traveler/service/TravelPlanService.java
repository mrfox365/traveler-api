package com.example.traveler.service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.traveler.dto.*;
import com.example.traveler.model.TravelPlan;
import com.example.traveler.repository.TravelPlanRepository;

import java.util.UUID;
import java.util.stream.Collectors;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class TravelPlanService {

    private final TravelPlanRepository planRepository;

    @Transactional(readOnly = true)
    public Page<PlanSummaryResponse> getAllPlans(Pageable pageable) {
        return planRepository.findAll(pageable).map(this::toPlanSummaryResponse);
    }

    @Transactional(readOnly = true)
    public PlanResponse getPlanById(UUID id) {
        TravelPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Plan not found with id: " + id));
        return toPlanResponse(plan);
    }

    public PlanResponse createPlan(CreatePlanRequest request) {

        if (request.endDate() != null && request.startDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new IllegalStateException("End date cannot be before start date");
        }

        TravelPlan plan = new TravelPlan();
        plan.setTitle(request.title());
        plan.setDescription(request.description());
        plan.setStartDate(request.startDate());
        plan.setEndDate(request.endDate());
        plan.setBudget(request.budget());
        if (request.currency() != null) {
            plan.setCurrency(request.currency());
        }
        plan.setPublic(request.isPublic());
        TravelPlan savedPlan = planRepository.save(plan);
        return toPlanResponse(savedPlan);
    }

    /**
     * Вирішення Проблеми 1: Оновлення з перевіркою версії .
     */
    public PlanResponse updatePlan(UUID id, UpdatePlanRequest request) {

        if (request.endDate() != null && request.startDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new IllegalStateException("End date cannot be before start date");
        }

        TravelPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Plan not found with id: " + id));

        // 1. Перевірка версії
        if (!plan.getVersion().equals(request.version())) {
            // Кидаємо помилку, яку обробить @ControllerAdvice і поверне 409 Conflict
            throw new OptimisticLockException("Conflict: Plan (id: " + id + ") was updated by another user. Please refresh.");
        }

        // 2. Оновлення полів
        plan.setTitle(request.title());
        plan.setDescription(request.description());
        plan.setStartDate(request.startDate());
        plan.setEndDate(request.endDate());
        plan.setBudget(request.budget());
        plan.setCurrency(request.currency());

        // 3. Збереження. JPA автоматично інкрементує версію.
        TravelPlan updatedPlan = planRepository.saveAndFlush(plan);
        return toPlanResponse(updatedPlan);
    }

    public void deletePlan(UUID id) {
        if (!planRepository.existsById(id)) {
            throw new EntityNotFoundException("Plan not found with id: " + id);
        }
        planRepository.deleteById(id); // Спрацює Cascade Delete для локацій [cite: 48]
    }

    private PlanResponse toPlanResponse(TravelPlan plan) {
        List<LocationDTO> locationDtos = plan.getLocations().stream()
                .map(loc -> new LocationDTO(
                        loc.getId(),
                        loc.getTravelPlan().getId(),
                        loc.getName(),
                        loc.getAddress(),
                        loc.getLatitude(),
                        loc.getLongitude(),
                        loc.getVisitOrder(),
                        loc.getNotes(),
                        loc.getBudget(),
                        loc.getVersion()))
                .collect(Collectors.toList());


        return new PlanResponse(
                plan.getId(), plan.getTitle(), plan.getDescription(),
                plan.getStartDate(), plan.getEndDate(), plan.getBudget(), plan.getCurrency(),
                plan.getVersion(), plan.isPublic(), locationDtos);
    }

    private PlanSummaryResponse toPlanSummaryResponse(TravelPlan plan) {
        return new PlanSummaryResponse(
                plan.getId(),
                plan.getTitle(),
                plan.getStartDate(),
                plan.getEndDate(),
                plan.getVersion()
        );
    }
}