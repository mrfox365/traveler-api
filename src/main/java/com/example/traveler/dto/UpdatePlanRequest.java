package com.example.traveler.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdatePlanRequest(
        @NotBlank @Size(max = 200) String title,
        String description,
        LocalDate startDate,
        LocalDate endDate,

        @DecimalMin("0.0")
        @Digits(integer = 8, fraction = 2, message = "Budget must have up to 8 integer digits and 2 fraction digits")
        BigDecimal budget,

        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be 3 uppercase letters (ISO 4217)")
        String currency,

        @NotNull
        @Min(value = 0, message = "Version cannot be negative")
        Integer version
) {}