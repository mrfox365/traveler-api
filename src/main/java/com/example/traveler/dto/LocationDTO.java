package com.example.traveler.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record LocationDTO(
        UUID id,
        UUID travelPlanId,
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        Integer visitOrder,
        String notes,
        BigDecimal budget,
        Integer version
) {}