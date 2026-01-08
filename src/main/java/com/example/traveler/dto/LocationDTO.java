package com.example.traveler.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;
import java.time.OffsetDateTime;

public record LocationDTO(
        UUID id,
        @JsonProperty("travelPlanId") UUID travelPlanId,
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        @JsonProperty("visitOrder") Integer visitOrder,
        String notes,
        @JsonProperty("arrivalDate") OffsetDateTime arrivalDate,
        @JsonProperty("departureDate") OffsetDateTime departureDate,
        BigDecimal budget,
        Integer version
) {}