package com.example.traveler.dto;

import java.time.LocalDate;
import java.util.UUID;

public record PlanSummaryResponse(
        UUID id,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        Integer version
) {}