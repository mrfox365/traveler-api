package com.example.traveler;

import com.example.traveler.dto.CreateLocationRequest; // Використаємо для початкового DTO
import com.example.traveler.dto.UpdateLocationRequest;
import com.example.traveler.model.Location;
import com.example.traveler.model.TravelPlan;
import com.example.traveler.repository.LocationRepository;
import com.example.traveler.repository.TravelPlanRepository;
import com.example.traveler.service.LocationService;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class ConcurrentLocationUpdateTests {

    private static final Logger log = LoggerFactory.getLogger(ConcurrentLocationUpdateTests.class);

    @Autowired
    private LocationService locationService;

    @Autowired
    private TravelPlanRepository travelPlanRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private UUID planId;
    private UUID locationId;

    // Збережемо початкові значення для створення DTO
    private static final String INITIAL_NAME = "Eiffel Tower";
    private static final String INITIAL_ADDRESS = "Champ de Mars, Paris";
    private static final BigDecimal INITIAL_BUDGET = new BigDecimal("50.00");
    private static final String INITIAL_NOTES = "Book tickets";
    private static final BigDecimal INITIAL_LAT = new BigDecimal("48.8584");
    private static final BigDecimal INITIAL_LON = new BigDecimal("2.2945");
    private static final Integer INITIAL_ORDER = 1;
    private static final Integer INITIAL_VERSION = 0; // Початкова версія після першого save()

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);

        // Створюємо початковий план та локацію
        locationId = transactionTemplate.execute(status -> {
            locationRepository.deleteAll();
            travelPlanRepository.deleteAll();

            TravelPlan plan = new TravelPlan();
            plan.setTitle("Paris Trip");
            plan.setBudget(new BigDecimal("1000.0"));
            TravelPlan savedPlan = travelPlanRepository.saveAndFlush(plan);
            planId = savedPlan.getId();

            Location location = new Location();
            location.setName(INITIAL_NAME);
            location.setAddress(INITIAL_ADDRESS);
            location.setLatitude(INITIAL_LAT);
            location.setLongitude(INITIAL_LON);
            location.setArrivalDate(null);
            location.setDepartureDate(null);
            location.setBudget(INITIAL_BUDGET);
            location.setNotes(INITIAL_NOTES);
            location.setTravelPlan(savedPlan);
            location.setVisitOrder(INITIAL_ORDER);
            // @Version поле буде автоматично 0 тут

            Location savedLocation = locationRepository.saveAndFlush(location);
            log.info("Створено тестову локацію ID: {}, Version: {}", savedLocation.getId(), savedLocation.getVersion());
            return savedLocation.getId();
        });
    }

    @Test
    void testConcurrentLocationUpdate_ShouldFailWithoutVersion_AndPassWithVersion() throws InterruptedException {
        int threadCount = 2;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        var executor = Executors.newFixedThreadPool(threadCount);

        AtomicBoolean userASucceeded = new AtomicBoolean(false);
        AtomicBoolean userACaughtException = new AtomicBoolean(false);
        AtomicBoolean userBSucceeded = new AtomicBoolean(false);
        AtomicBoolean userBCaughtException = new AtomicBoolean(false);

        // --- Потік A (User A - оновлює budget) ---
        executor.submit(() -> {
            try {
                UpdateLocationRequest userARequest = new UpdateLocationRequest(
                        INITIAL_NAME,
                        INITIAL_ADDRESS,
                        INITIAL_LAT,
                        INITIAL_LON,
                        null, // arrivalDate
                        null, // departureDate
                        new BigDecimal("75.00"), // <-- Budget
                        INITIAL_NOTES, // <-- Notes
                        INITIAL_ORDER, // visitOrder
                        INITIAL_VERSION  // version
                );

                startLatch.await(); // Чекаємо на "постріл"
                log.info("ПОТІК A: СТАРТ. Оновлює budget до 75...");
                locationService.updateLocation(locationId, userARequest);
                userASucceeded.set(true);

            } catch (OptimisticLockException | ObjectOptimisticLockingFailureException e) {
                log.warn("ПОТІК A: Спіймано очікуваний OptimisticLockException");
                userACaughtException.set(true);
            } catch (Exception e) {
                log.error("ПОТІК A: Неочікувана помилка", e);
            } finally {
                log.info("ПОТІК A: ФІНІШ.");
                doneLatch.countDown();
            }
        });

        // --- Потік B (User B - оновлює notes) ---
        executor.submit(() -> {
            try {
                UpdateLocationRequest userBRequest = new UpdateLocationRequest(
                        INITIAL_NAME,
                        INITIAL_ADDRESS,
                        INITIAL_LAT,
                        INITIAL_LON,
                        null, // arrivalDate
                        null, // departureDate
                        INITIAL_BUDGET, // <-- Budget
                        "Tickets booked!", // <-- Notes
                        INITIAL_ORDER, // visitOrder
                        INITIAL_VERSION  // version
                );

                startLatch.await(); // Чекаємо на "постріл"
                log.info("ПОТІК B: СТАРТ. Оновлює notes...");
                locationService.updateLocation(locationId, userBRequest);
                userBSucceeded.set(true);

            } catch (OptimisticLockException | ObjectOptimisticLockingFailureException e) {
                log.warn("ПОТІК B: Спіймано очікуваний OptimisticLockException");
                userBCaughtException.set(true);
            } catch (Exception e) {
                log.error("ПОТІК B: Неочікувана помилка", e);
            } finally {
                log.info("ПОТІК B: ФІНІШ.");
                doneLatch.countDown();
            }
        });

        Thread.sleep(100); // Даємо потокам час "підготуватися"
        startLatch.countDown(); // "Постріл!"

        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Потоки не завершились вчасно");
        executor.shutdown();

        // --- Перевірка результатів ---
        log.info("Перевірка результатів: userASucceeded={}, userACaughtException={}, userBSucceeded={}, userBCaughtException={}",
                userASucceeded.get(), userACaughtException.get(), userBSucceeded.get(), userBCaughtException.get());

        Location finalLocation = locationRepository.findById(locationId)
                .orElseThrow(() -> new EntityNotFoundException("Фінальна локація не знайдена!"));

        log.info("Кінцевий стан - Budget: [{}], Notes: [{}]", finalLocation.getBudget(), finalLocation.getNotes());

        // **ГОЛОВНА ПЕРЕВІРКА**
        // Ми очікуємо, що ОДИН потік впаде з помилкою, а ІНШИЙ - пройде.
        // (userASucceeded XOR userBSucceeded) ТА (userACaughtException XOR userBCaughtException)
        boolean oneSucceeded = userASucceeded.get() ^ userBSucceeded.get();
        boolean oneFailedWithLock = userACaughtException.get() ^ userBCaughtException.get();

        // Цей 'assert' впаде, якщо @Version відсутній,
        // тому що обидва (A і B) встановлять userSucceeded = true.
        assertTrue(oneSucceeded && oneFailedWithLock,
                "Один потік мав успішно завершитись, а інший - впасти з OptimisticLockException. " +
                        "Якщо цього не сталось, @Version, ймовірно, відсутній у Location.java");

        // Додаткова перевірка, що дані в БД відповідають тому потоку, який "переміг"
        if (userASucceeded.get()) {
            log.info("Переміг Потік A (Budget)");
            assertThat(finalLocation.getBudget()).isEqualByComparingTo(new BigDecimal("75.00"));
            assertThat(finalLocation.getNotes()).isEqualTo(INITIAL_NOTES);
        } else {
            log.info("Переміг Потік B (Notes)");
            assertThat(finalLocation.getBudget()).isEqualByComparingTo(INITIAL_BUDGET);
            assertThat(finalLocation.getNotes()).isEqualTo("Tickets booked!");
        }

        // Перевіряємо, що версія збільшилась (якщо вона є)
        if (finalLocation.getVersion() != null) {
            assertThat(finalLocation.getVersion()).isEqualTo(1L); // Припускаємо, що початкова версія 0
        }
    }
}

