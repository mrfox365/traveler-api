package com.example.traveler.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "locations",
        indexes = {
                @Index(name = "idx_location_travel_plan_id", columnList = "travel_plan_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"travel_plan_id", "visit_order"})
        }
)
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    // Зворотній зв'язок до плану подорожі
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_plan_id", nullable = false)
    private TravelPlan travelPlan;

    @Size(min = 1, max = 200)
    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String address;

    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    @Column(precision = 10, scale = 6)
    private BigDecimal latitude;

    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    @Column(precision = 11, scale = 6)
    private BigDecimal longitude;

    // Поле для вирішення Проблеми 2 (auto-order)
    @Column(nullable = false)
    private Integer visitOrder;

    private OffsetDateTime arrivalDate;
    private OffsetDateTime departureDate;

    @DecimalMin(value = "0.0")
    @Column(precision = 10, scale = 2)
    private BigDecimal budget;

    @Column(columnDefinition = "text")
    private String notes;

    @Version
    private Integer version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    // Перевірка дат
    @PrePersist
    @PreUpdate
    private void validateDates() {
        if (departureDate != null && arrivalDate != null && departureDate.isBefore(arrivalDate)) {
            throw new IllegalStateException("Departure date cannot be before arrival date");
        }
    }
}