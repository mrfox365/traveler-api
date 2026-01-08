package com.example.traveler.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PlanResponse(
        UUID id,
        String title,
        String description,

        @JsonProperty("startDate") LocalDate startDate,
        @JsonProperty("endDate") LocalDate endDate,

        BigDecimal budget,
        String currency,
        Integer version,

        @JsonProperty("isPublic") boolean isPublic,

        List<LocationDTO> locations
) {}