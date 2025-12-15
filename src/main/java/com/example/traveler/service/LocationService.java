package com.example.traveler.service;

import com.example.traveler.config.ShardContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import com.example.traveler.dto.CreateLocationRequest;
import com.example.traveler.dto.LocationDTO;
import com.example.traveler.dto.UpdateLocationRequest;
import com.example.traveler.model.Location;
import com.example.traveler.model.TravelPlan;
import com.example.traveler.repository.LocationRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locationRepository;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate; // Додаємо це

    public LocationDTO addLocationToPlan(UUID planId, CreateLocationRequest request) {
        if (request.departureDate() != null && request.arrivalDate() != null && request.departureDate().isBefore(request.arrivalDate())) {
            throw new IllegalStateException("Departure date cannot be before arrival date");
        }

        String shardKey = getShardKey(planId);
        ShardContext.setShard(shardKey);

        try {
            // Відкриваємо транзакцію програмно
            return transactionTemplate.execute(status -> {
                TravelPlan plan = entityManager.find(
                        TravelPlan.class,
                        planId,
                        LockModeType.PESSIMISTIC_WRITE
                );

                if (plan == null) {
                    throw new EntityNotFoundException("Plan not found with id: " + planId);
                }

                int maxOrder = locationRepository.findMaxVisitOrderByTravelPlanId(planId)
                        .orElse(0);

                Location location = new Location();

                // Генеруємо ID в тому ж шарді
                UUID locationId = generateIdForShard(shardKey);
                location.setId(locationId);

                location.setName(request.name());
                location.setAddress(request.address());
                location.setNotes(request.notes());
                location.setBudget(request.budget());
                location.setLatitude(request.latitude());
                location.setLongitude(request.longitude());
                location.setArrivalDate(request.arrivalDate());
                location.setDepartureDate(request.departureDate());
                location.setTravelPlan(plan);
                location.setVisitOrder(maxOrder + 1);

                return toLocationDTO(locationRepository.save(location));
            });
        } finally {
            ShardContext.clear();
        }
    }

    public LocationDTO updateLocation(UUID locationId, UpdateLocationRequest request) {
        if (request.departureDate() != null && request.arrivalDate() != null && request.departureDate().isBefore(request.arrivalDate())) {
            throw new IllegalStateException("Departure date cannot be before arrival date");
        }

        String shardKey = getShardKey(locationId);
        ShardContext.setShard(shardKey);

        try {
            return transactionTemplate.execute(status -> {
                Location location = locationRepository.findById(locationId)
                        .orElseThrow(() -> new EntityNotFoundException("Location not found with id: " + locationId));

                if (!location.getVersion().equals(request.version())) {
                    throw new OptimisticLockException("Conflict: Location (id: " + locationId + ") was updated by another user. Please refresh.");
                }

                location.setName(request.name());
                location.setAddress(request.address());
                location.setNotes(request.notes());
                location.setBudget(request.budget());
                location.setLatitude(request.latitude());
                location.setLongitude(request.longitude());
                location.setArrivalDate(request.arrivalDate());
                location.setDepartureDate(request.departureDate());

                if (request.visitOrder() != null) {
                    location.setVisitOrder(request.visitOrder());
                }

                return toLocationDTO(locationRepository.saveAndFlush(location));
            });
        } finally {
            ShardContext.clear();
        }
    }

    public void deleteLocation(UUID locationId) {
        String shardKey = getShardKey(locationId);
        ShardContext.setShard(shardKey);

        try {
            transactionTemplate.execute(status -> {
                if (!locationRepository.existsById(locationId)) {
                    throw new EntityNotFoundException("Location not found with id: " + locationId);
                }
                locationRepository.deleteById(locationId);
                return null;
            });
        } finally {
            ShardContext.clear();
        }
    }

    private LocationDTO toLocationDTO(Location loc) {
        return new LocationDTO(
                loc.getId(), loc.getTravelPlan().getId(), loc.getName(), loc.getAddress(),
                loc.getLatitude(), loc.getLongitude(), loc.getVisitOrder(), loc.getNotes(),
                loc.getBudget(), loc.getVersion());
    }

    private String getShardKey(UUID id) {
        String uuidStr = id.toString();
        return String.valueOf(uuidStr.charAt(uuidStr.length() - 1));
    }

    private UUID generateIdForShard(String targetShardKey) {
        UUID uuid;
        do {
            uuid = UUID.randomUUID();
        } while (!getShardKey(uuid).equals(targetShardKey));
        return uuid;
    }
}