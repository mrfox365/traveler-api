package com.example.traveler.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "travel_plans")
public class TravelPlan {

    @Id
    private UUID id;

    @Size(min = 1, max = 200)
    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    private LocalDate startDate;

    private LocalDate endDate;

    @DecimalMin(value = "0.0")
    @Column(precision = 10, scale = 2)
    private BigDecimal budget;

    @Column(length = 3, columnDefinition = "varchar(3) default 'USD'")
    private String currency = "USD";

    @Column(columnDefinition = "boolean default false")
    private boolean isPublic = false;

    @Version
    private Integer version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    // Зв'язок з локаціями
    @OneToMany(
            mappedBy = "travelPlan",
            cascade = CascadeType.ALL, // Забезпечує CASCADE DELETE [cite: 44, 48]
            orphanRemoval = true
    )
    @OrderBy("visitOrder ASC") // Локації завжди будуть впорядковані
    private List<Location> locations = new ArrayList<>();

    // ВИДАЛІТЬ ЦЕЙ МЕТОД
    @PrePersist
    @PreUpdate
    private void validateDates() {
        if (endDate != null && startDate != null && endDate.isBefore(startDate)) {
            throw new IllegalStateException("End date cannot be before start date");
        }
    }
}