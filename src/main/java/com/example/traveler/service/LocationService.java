package com.example.traveler.service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.traveler.dto.CreateLocationRequest;
import com.example.traveler.dto.LocationDTO;
import com.example.traveler.dto.UpdateLocationRequest;
import com.example.traveler.model.Location;
import com.example.traveler.model.TravelPlan;
import com.example.traveler.repository.LocationRepository;
import com.example.traveler.repository.TravelPlanRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class LocationService {

    private final LocationRepository locationRepository;
    private final TravelPlanRepository planRepository;

    public LocationDTO addLocationToPlan(UUID planId, CreateLocationRequest request) {
        if (request.departureDate() != null && request.arrivalDate() != null && request.departureDate().isBefore(request.arrivalDate())) {
            throw new IllegalStateException("Departure date cannot be before arrival date");
        }

        TravelPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new EntityNotFoundException("Plan not found with id: " + planId));

        int maxOrder = locationRepository.findMaxVisitOrderByTravelPlanId(planId)
                .orElse(0);

        Location location = new Location();
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

        Location savedLocation = locationRepository.save(location);

        return toLocationDTO(savedLocation);
    }

    public LocationDTO updateLocation(UUID locationId, UpdateLocationRequest request) {
        if (request.departureDate() != null && request.arrivalDate() != null && request.departureDate().isBefore(request.arrivalDate())) {
            throw new IllegalStateException("Departure date cannot be before arrival date");
        }

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

        if(request.visitOrder() != null) {
            location.setVisitOrder(request.visitOrder());
        }

        Location updatedLocation = locationRepository.saveAndFlush(location);
        return toLocationDTO(updatedLocation);
    }

    public void deleteLocation(UUID locationId) {
        if (!locationRepository.existsById(locationId)) {
            throw new EntityNotFoundException("Location not found with id: " + locationId);
        }
        locationRepository.deleteById(locationId);
    }

    private LocationDTO toLocationDTO(Location loc) {
        return new LocationDTO(
                loc.getId(), loc.getTravelPlan().getId(), loc.getName(), loc.getAddress(),
                loc.getLatitude(), loc.getLongitude(), loc.getVisitOrder(), loc.getNotes(),
                loc.getBudget(), loc.getVersion());
    }
}