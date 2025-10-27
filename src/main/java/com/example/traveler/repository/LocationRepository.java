package com.example.traveler.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.example.traveler.model.Location;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LocationRepository extends JpaRepository<Location, UUID> {

    /**
     * Знаходить максимальний visitOrder для заданого travel_plan_id.
     * Це потрібно для вирішення Проблеми 2
     * та реалізації auto-order.
     */
    @Query("SELECT MAX(l.visitOrder) FROM Location l WHERE l.travelPlan.id = :planId")
    Optional<Integer> findMaxVisitOrderByTravelPlanId(@Param("planId") UUID planId);

    List<Location> findAllByTravelPlanIdOrderByVisitOrderAsc(UUID travelPlanId);
}