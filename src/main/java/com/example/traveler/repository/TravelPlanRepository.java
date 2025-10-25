package com.example.traveler.repository;

import com.example.traveler.model.TravelPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface TravelPlanRepository extends JpaRepository<TravelPlan, UUID> {

}