package com.example.traveler.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PlanSummaryResponse(
        UUID id,
        String title,

        @JsonProperty("startDate") LocalDate startDate,
        @JsonProperty("endDate") LocalDate endDate,

        BigDecimal budget,
        String currency,

        @JsonProperty("isPublic") boolean isPublic,

        Integer version
) {}