package com.example.traveler;

import com.example.traveler.dto.CreateLocationRequest;
import com.example.traveler.model.Location;
import com.example.traveler.model.TravelPlan;
import com.example.traveler.repository.LocationRepository;
import com.example.traveler.repository.TravelPlanRepository;
import com.example.traveler.service.LocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class ConcurrentLocationTests {

    private static final Logger log = LoggerFactory.getLogger(ConcurrentLocationTests.class);

    @Autowired
    private LocationService locationService; // Тестуємо LocationService

    @Autowired
    private TravelPlanRepository travelPlanRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private UUID planId;

    @BeforeEach
    void setUp() {
        // --- Налаштування ---
        // Використовуємо TransactionTemplate для чистого налаштування БД перед кожним тестом
        transactionTemplate = new TransactionTemplate(transactionManager);

        planId = transactionTemplate.execute(status -> {
            // Повністю чистимо репозиторії
            // Використовуємо deleteAllInBatch для швидкості та уникнення N+1
            locationRepository.deleteAllInBatch();
            travelPlanRepository.deleteAllInBatch();

            // Створюємо 1 план
            TravelPlan plan = new TravelPlan();
            plan.setTitle("Європа");
            plan.setBudget(new BigDecimal("2000.0"));
            TravelPlan savedPlan = travelPlanRepository.save(plan);

            // Створюємо 2 початкові локації
            Location paris = new Location();
            paris.setName("Paris");
            paris.setVisitOrder(1);
            paris.setTravelPlan(savedPlan);
            locationRepository.save(paris);

            Location rome = new Location();
            rome.setName("Rome");
            rome.setVisitOrder(2);
            rome.setTravelPlan(savedPlan);
            locationRepository.save(rome);

            log.info("Створено тестовий план ID: {} з 2 локаціями (Paris=1, Rome=2)", savedPlan.getId());
            return savedPlan.getId();
        });

        // Перевірка налаштування (про всяк випадок)
        List<Location> initialLocations = locationRepository.findAllByTravelPlanIdOrderByVisitOrderAsc(planId);
        assertThat(initialLocations).hasSize(2);
    }

    @Test
    void testConcurrentLocationAdditionCausesRaceCondition() throws InterruptedException {
        int threadCount = 2;
        // Бар'єр, який змусить обидва потоки стартувати одночасно
        CountDownLatch startLatch = new CountDownLatch(1);
        // Лічильник, який дозволить головному потоку дочекатися завершення обох
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        var executor = Executors.newFixedThreadPool(threadCount);

        log.info("Починаємо тест паралельного додавання...");

        // --- Запит A (User A - додає Madrid) ---
        CreateLocationRequest madridRequest = new CreateLocationRequest(
                "Madrid", // name (String)
                "Plaza Mayor", // address (String)
                new BigDecimal("40.415"), // latitude (BigDecimal)
                new BigDecimal("-3.707"), // longitude (BigDecimal)
                null, // arrivalDate (OffsetDateTime)
                null, // departureDate (OffsetDateTime)
                null, // budget (BigDecimal)
                null); // notes (String)

        // --- Запит B (User B - додає Barcelona) ---
        CreateLocationRequest barcelonaRequest = new CreateLocationRequest(
                "Barcelona", // name (String)
                "Sagrada Familia", // address (String)
                new BigDecimal("41.403"), // latitude (BigDecimal)
                new BigDecimal("2.174"), // longitude (BigDecimal)
                null, // arrivalDate (OffsetDateTime)
                null, // departureDate (OffsetDateTime)
                null, // budget (BigDecimal)
                null); // notes (String)


        // --- Потік A ---
        executor.submit(() -> {
            try {
                startLatch.await(); // Чекаємо на "постріл"
                log.info("ПОТІК A: СТАРТ. Додає Madrid...");
                locationService.addLocationToPlan(planId, madridRequest);
                log.info("ПОТІК A: ФІНІШ.");
            } catch (Exception e) {
                log.error("ПОТІК A: Помилка!", e);
            } finally {
                doneLatch.countDown();
            }
        });

        // --- Потік B ---
        executor.submit(() -> {
            try {
                startLatch.await(); // Чекаємо на "постріл"
                log.info("ПОТІК B: СТАРТ. Додає Barcelona...");
                locationService.addLocationToPlan(planId, barcelonaRequest);
                log.info("ПОТІК B: ФІНІШ.");
            } catch (Exception e) {
                log.error("ПОТІК B: Помилка!", e);
            } finally {
                doneLatch.countDown();
            }
        });

        Thread.sleep(100); // Даємо потокам час підготуватися і дійти до .await()
        startLatch.countDown(); // "Постріл!" - запускаємо обидва потоки одночасно

        // Чекаємо до 10 секунд, доки обидва потоки не завершаться
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Потоки не завершились вчасно");
        executor.shutdown();
        log.info("Обидва потоки завершили роботу. Перевірка результатів...");

        // --- Перевірка результатів ---
        // Використовуємо метод, який ми додали в репозиторій
        List<Location> finalLocations = locationRepository.findAllByTravelPlanIdOrderByVisitOrderAsc(planId);

        // Збираємо 'visitOrder' з результатів
        List<Integer> finalOrders = finalLocations.stream()
                .map(Location::getVisitOrder)
                .collect(Collectors.toList());

        log.info("Кінцевий список 'visitOrder' у базі: {}", finalOrders);
        log.info("Всього локацій знайдено: {}", finalLocations.size());

        // **ГОЛОВНА ПЕРЕВІрка**
        // Ми очікуємо, що в базі буде 4 локації (2 початкові + 2 нові)
        assertThat(finalLocations).hasSize(4);

        // **ГОЛОВНА ПЕРЕВІРКА НА "СТАН ГОНИТВИ"**
        // Ми очікуємо, що 'orders' будуть [1, 2, 3, 4]
        // Якщо код вразливий, то отримуємо [1, 2, 3, 3]
        assertThat(finalOrders)
                .as("Orders мають бути унікальними та послідовними. " +
                        "Якщо цей тест впав (напр. [1, 2, 3, 3]), " +
                        "це означає, що 'race condition' відбулося.")
                .containsExactly(1, 2, 3, 4);

        log.info("Тест успішно пройдено! 'Race condition' не відбулося (або було вирішене).");
    }
}

