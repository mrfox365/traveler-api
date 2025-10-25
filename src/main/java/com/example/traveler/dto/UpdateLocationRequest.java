package com.example.traveler.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record UpdateLocationRequest(
        @NotBlank @Size(max = 200) String name,
        String address,

        @DecimalMin(value = "-90.0")
        @DecimalMax(value = "90.0")
        BigDecimal latitude,

        @DecimalMin(value = "-180.0")
        @DecimalMax(value = "180.0")
        BigDecimal longitude,

        OffsetDateTime arrivalDate,
        OffsetDateTime departureDate,

        @DecimalMin("0.0")
        @Digits(integer = 8, fraction = 2, message = "Budget must have up to 8 integer digits and 2 fraction digits")
        BigDecimal budget,

        String notes,
        Integer visitOrder,

        @NotNull
        @Min(value = 0, message = "Version cannot be negative")
        Integer version
) {}