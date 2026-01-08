package com.example.traveler.service;

import com.example.traveler.config.ShardContext;
import com.example.traveler.dto.*;
import com.example.traveler.model.TravelPlan;
import com.example.traveler.repository.TravelPlanRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TravelPlanService {

    private final TravelPlanRepository planRepository;
    private final TransactionTemplate transactionTemplate;

    private static final String[] SHARDS = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f"};
    private final ExecutorService executorService = Executors.newFixedThreadPool(16);

    public Page<PlanSummaryResponse> getAllPlans(Pageable pageable) {
        long totalElements = getTotalCountFromAllShards();

        if (totalElements == 0) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        int neededSize = (int) pageable.getOffset() + pageable.getPageSize();
        Pageable internalPageable = PageRequest.of(0, neededSize, pageable.getSort());

        List<TravelPlan> aggregatedResults = getDataFromAllShards(internalPageable);
        sortInMemory(aggregatedResults, pageable);

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), aggregatedResults.size());

        List<PlanSummaryResponse> pagedContent;
        if (start > aggregatedResults.size()) {
            pagedContent = Collections.emptyList();
        } else {
            pagedContent = aggregatedResults.subList(start, end).stream()
                    .map(this::toPlanSummaryResponse)
                    .collect(Collectors.toList());
        }

        return new PageImpl<>(pagedContent, pageable, totalElements);
    }

    private long getTotalCountFromAllShards() {
        List<CompletableFuture<Long>> futures = new ArrayList<>();
        for (String shard : SHARDS) {
            CompletableFuture<Long> future = CompletableFuture.supplyAsync(() -> {
                ShardContext.setShard(shard);
                try {
                    return planRepository.count();
                } finally {
                    ShardContext.clear();
                }
            }, executorService);
            futures.add(future);
        }
        return futures.stream().map(CompletableFuture::join).reduce(0L, Long::sum);
    }

    private List<TravelPlan> getDataFromAllShards(Pageable pageable) {
        List<CompletableFuture<List<TravelPlan>>> futures = new ArrayList<>();
        for (String shard : SHARDS) {
            CompletableFuture<List<TravelPlan>> future = CompletableFuture.supplyAsync(() -> {
                ShardContext.setShard(shard);
                try {
                    return planRepository.findAll(pageable).getContent();
                } finally {
                    ShardContext.clear();
                }
            }, executorService);
            futures.add(future);
        }
        return futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    private void sortInMemory(List<TravelPlan> plans, Pageable pageable) {
        if (pageable.getSort().isUnsorted()) return;

        Comparator<TravelPlan> comparator = null;
        for (org.springframework.data.domain.Sort.Order order : pageable.getSort()) {
            Comparator<TravelPlan> currentComparator = getComparatorForProperty(order.getProperty());
            if (currentComparator == null) continue;
            if (order.isDescending()) currentComparator = currentComparator.reversed();
            if (comparator == null) comparator = currentComparator;
            else comparator = comparator.thenComparing(currentComparator);
        }
        if (comparator != null) plans.sort(comparator);
    }

    private Comparator<TravelPlan> getComparatorForProperty(String property) {
        switch (property) {
            case "title": return Comparator.comparing(TravelPlan::getTitle, Comparator.nullsLast(String::compareToIgnoreCase));
            case "startDate": return Comparator.comparing(TravelPlan::getStartDate, Comparator.nullsLast(Comparator.naturalOrder()));
            case "endDate": return Comparator.comparing(TravelPlan::getEndDate, Comparator.nullsLast(Comparator.naturalOrder()));
            case "budget": return Comparator.comparing(TravelPlan::getBudget, Comparator.nullsLast(Comparator.naturalOrder()));
            case "currency": return Comparator.comparing(TravelPlan::getCurrency, Comparator.nullsLast(String::compareToIgnoreCase));
            case "id": return Comparator.comparing(TravelPlan::getId, Comparator.nullsLast(Comparator.naturalOrder()));
            case "createdAt": return Comparator.comparing(TravelPlan::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
            default: return null;
        }
    }

    public PlanResponse getPlanById(UUID id) {
        String shardKey = getShardKey(id);
        ShardContext.setShard(shardKey);
        try {
            return transactionTemplate.execute(status -> {
                TravelPlan plan = planRepository.findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("Plan not found with id: " + id));
                return toPlanResponse(plan);
            });
        } finally {
            ShardContext.clear();
        }
    }

    public PlanResponse createPlan(CreatePlanRequest request) {
        if (request.endDate() != null && request.startDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new IllegalStateException("End date cannot be before start date");
        }
        UUID id = UUID.randomUUID();
        String shardKey = getShardKey(id);
        ShardContext.setShard(shardKey);
        try {
            return transactionTemplate.execute(status -> {
                TravelPlan plan = new TravelPlan();
                plan.setId(id);
                plan.setTitle(request.title());
                plan.setDescription(request.description());
                plan.setStartDate(request.startDate());
                plan.setEndDate(request.endDate());
                plan.setBudget(request.budget());
                if (request.currency() != null) plan.setCurrency(request.currency());
                plan.setPublic(request.isPublic()); // Тепер це працюватиме коректно
                TravelPlan savedPlan = planRepository.save(plan);
                return toPlanResponse(savedPlan);
            });
        } finally {
            ShardContext.clear();
        }
    }

    public PlanResponse updatePlan(UUID id, UpdatePlanRequest request) {
        if (request.endDate() != null && request.startDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new IllegalStateException("End date cannot be before start date");
        }
        String shardKey = getShardKey(id);
        ShardContext.setShard(shardKey);
        try {
            return transactionTemplate.execute(status -> {
                TravelPlan plan = planRepository.findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("Plan not found with id: " + id));
                if (!plan.getVersion().equals(request.version())) {
                    throw new OptimisticLockException("Conflict: Plan (id: " + id + ") was updated by another user. Please refresh.");
                }
                plan.setTitle(request.title());
                plan.setDescription(request.description());
                plan.setStartDate(request.startDate());
                plan.setEndDate(request.endDate());
                plan.setBudget(request.budget());
                plan.setCurrency(request.currency());
                plan.setPublic(request.isPublic()); // Додано оновлення статусу Public/Private
                TravelPlan updatedPlan = planRepository.saveAndFlush(plan);
                return toPlanResponse(updatedPlan);
            });
        } finally {
            ShardContext.clear();
        }
    }

    public void deletePlan(UUID id) {
        String shardKey = getShardKey(id);
        ShardContext.setShard(shardKey);
        try {
            transactionTemplate.execute(status -> {
                if (!planRepository.existsById(id)) {
                    throw new EntityNotFoundException("Plan not found with id: " + id);
                }
                planRepository.deleteById(id);
                return null;
            });
        } finally {
            ShardContext.clear();
        }
    }

    private String getShardKey(UUID id) {
        String uuidStr = id.toString();
        return String.valueOf(uuidStr.charAt(uuidStr.length() - 1));
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
                        loc.getArrivalDate(),
                        loc.getDepartureDate(),
                        loc.getBudget(),
                        loc.getVersion()))
                .collect(Collectors.toList());

        return new PlanResponse(
                plan.getId(), plan.getTitle(), plan.getDescription(),
                plan.getStartDate(), plan.getEndDate(), plan.getBudget(), plan.getCurrency(),
                plan.getVersion(), plan.isPublic(), locationDtos);
    }

    // Оновлений метод з новими полями
    private PlanSummaryResponse toPlanSummaryResponse(TravelPlan plan) {
        return new PlanSummaryResponse(
                plan.getId(),
                plan.getTitle(),
                plan.getStartDate(),
                plan.getEndDate(),
                plan.getBudget(),
                plan.getCurrency(),
                plan.isPublic(),
                plan.getVersion()
        );
    }
}