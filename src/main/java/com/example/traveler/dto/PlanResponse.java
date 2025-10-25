package com.example.traveler.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PlanResponse(
        UUID id,
        String title,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal budget,
        String currency,
        Integer version,

        boolean isPublic,
        List<LocationDTO> locations
) {}