package com.example.traveler.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonFormat;

public record CreatePlanRequest(
        @NotBlank @Size(max = 200)
        @JsonProperty("title") String title,

        @JsonProperty("description") String description,

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        @JsonProperty("startDate") LocalDate startDate,

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        @JsonProperty("endDate") LocalDate endDate,

        @DecimalMin("0.0")
        @Digits(integer = 8, fraction = 2)
        @JsonProperty("budget") BigDecimal budget,

        @Pattern(regexp = "^[A-Z]{3}$")
        @JsonProperty("currency") String currency,

        @JsonProperty("isPublic")
        boolean isPublic
) {}