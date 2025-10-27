package com.example.traveler;

import com.example.traveler.model.TravelPlan;
import com.example.traveler.repository.TravelPlanRepository;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class ConcurrentUpdateTests {

    private static final Logger log = LoggerFactory.getLogger(ConcurrentUpdateTests.class);

    @Autowired
    private TravelPlanRepository travelPlanRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    private UUID planId; // ID тепер UUID

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        planId = transactionTemplate.execute(status -> {
            travelPlanRepository.deleteAll();

            // Використовуємо конструктор та сеттери
            TravelPlan plan = new TravelPlan();
            plan.setTitle("Європа");
            plan.setDescription("Початковий план");
            plan.setBudget(new BigDecimal("2000.0")); // Використовуємо BigDecimal

            TravelPlan savedPlan = travelPlanRepository.save(plan);
            log.info("Створено тестовий план ID: {}, Version: {}", savedPlan.getId(), savedPlan.getVersion());
            return savedPlan.getId();
        });

        // Перевірка, що план створено коректно
        TravelPlan plan = travelPlanRepository.findById(planId).orElseThrow();
        assertThat(plan.getTitle()).isEqualTo("Європа");
        assertThat(plan.getBudget()).isEqualByComparingTo(new BigDecimal("2000.0")); // Коректна перевірка BigDecimal
        assertThat(plan.getVersion()).isNotNull();
        assertThat(plan.getVersion()).isEqualTo(0);
    }

    @Test
    void testConcurrentUpdateLeadsToOptimisticLockException() throws InterruptedException {
        // Синхронізатори потоків
        CountDownLatch userAReadPlan = new CountDownLatch(1);
        CountDownLatch userBDone = new CountDownLatch(1);

        // Атомарні змінні для фіксації результатів
        AtomicBoolean userACaughtException = new AtomicBoolean(false);
        AtomicReference<Exception> userAException = new AtomicReference<>();
        AtomicBoolean userBUpdatedSuccessfully = new AtomicBoolean(false);

        var executor = Executors.newFixedThreadPool(2);

        // --- Потік A (User A - той, що запізниться) ---
        executor.submit(() -> {
            transactionTemplate.execute(status -> {
                try {
                    // User A читає план (version 0)
                    TravelPlan planA = travelPlanRepository.findById(planId).orElseThrow();
                    log.info("User A: прочитав план, version: {}", planA.getVersion());
                    assertThat(planA.getVersion()).isEqualTo(0);

                    // User A чекає, поки User B прочитає дані, оновить їх і збереже
                    userAReadPlan.countDown(); // Повідомляємо User B, що ми прочитали
                    log.info("User A: чекає на User B...");
                    if (!userBDone.await(5, TimeUnit.SECONDS)) {
                        throw new RuntimeException("User B не завершив вчасно");
                    }
                    log.info("User A: відновив роботу, намагається оновити title...");

                    // User A намагається оновити title, використовуючи застарілий об'єкт (version 0)
                    planA.setTitle("Супер Європа");

                    // Використовуємо saveAndFlush(), щоб відправити SQL UPDATE
                    // негайно і згенерувати виняток всередині try-catch.
                    travelPlanRepository.saveAndFlush(planA);

                    // Якщо ми дійшли сюди, @Version не спрацював
                    log.error("User A: ЗБЕРЕЖЕННЯ ВДАЛОСЯ, ЦЕ ПОМИЛКА В ЛОГІЦІ БЛОКУВАННЯ!");

                    // ОНОВЛЕНО: Більш надійний блок catch
                } catch (ObjectOptimisticLockingFailureException e) { // CПОЧАТКУ ПЕРЕВІРЯЄМО SPRING
                    // ОЧІКУВАНИЙ РЕЗУЛЬТАТ! (Spring wrapper)
                    log.info("User A: Успішно спіймано Spring ObjectOptimisticLockingFailureException, як і очікувалось.");
                    userACaughtException.set(true);
                    userAException.set(e);
                } catch (OptimisticLockException e) { // ПОТІМ ПЕРЕВІРЯЄМО "ЧИСТИЙ" JPA
                    // ОЧІКУВАНИЙ РЕЗУЛЬТАТ!
                    log.info("User A: Успішно спіймано OptimisticLockException (стандарт JPA), як і очікувалось.");
                    userACaughtException.set(true);
                    userAException.set(e);
                } catch (DataAccessException e) { // ПЕРЕВІРЯЄМО ІНШІ DAO винятки
                    log.warn("User A: Спіймано DataAccessException, перевіряємо причину...");
                    if (e.getCause() instanceof OptimisticLockException) {
                        log.info("User A: Причиною є OptimisticLockException. Це очікувано.");
                        userACaughtException.set(true);
                        userAException.set(e);
                    } else {
                        log.error("User A: Спіймано DataAccessException, але це НЕ OptimisticLockException", e);
                        userAException.set(e);
                    }
                } catch (Exception e) {
                    log.error("User A: Спіймано неочікуваний виняток", e);
                    userAException.set(e);
                }
                return null;
            });
        });

        // --- Потік B (User B - той, що буде першим) ---
        executor.submit(() -> {
            transactionTemplate.execute(status -> {
                try {
                    // User B чекає, поки User A прочитає дані
                    if (!userAReadPlan.await(5, TimeUnit.SECONDS)) {
                        throw new RuntimeException("User A не прочитав дані вчасно");
                    }

                    // User B читає план (version 0)
                    TravelPlan planB = travelPlanRepository.findById(planId).orElseThrow();
                    log.info("User B: прочитав план, version: {}", planB.getVersion());
                    assertThat(planB.getVersion()).isEqualTo(0);

                    // User B оновлює budget і зберігає (version 0 -> 1)
                    planB.setBudget(new BigDecimal("2500.0"));

                    travelPlanRepository.saveAndFlush(planB);
                    userBUpdatedSuccessfully.set(true);
                    log.info("User B: успішно оновив budget, нова version: 1");

                } catch (Exception e) {
                    log.error("User B: Спіймано неочікуваний виняток", e);
                } finally {
                    userBDone.countDown(); // Повідомляємо User A, що ми закінчили
                }
                return null;
            });
        });

        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "Потоки не завершились вчасно");

        // --- Перевірка результатів ---
        log.info("Перевірка результатів: userBUpdatedSuccessfully={}, userACaughtException={}",
                userBUpdatedSuccessfully.get(), userACaughtException.get());

        assertTrue(userBUpdatedSuccessfully.get(), "User B (зміна бюджету) мав успішно зберегти дані");
        assertTrue(userACaughtException.get(), "User A (зміна title) мав отримати OptimisticLockException");

        // Фінальна перевірка стану в БД
        TravelPlan finalPlan = travelPlanRepository.findById(planId).orElseThrow();
        log.info("Кінцевий стан - Title: [{}], Budget: [{}], Version: [{}]",
                finalPlan.getTitle(), finalPlan.getBudget(), finalPlan.getVersion());

        // Переконуємось, що дані User B збережено
        assertThat(finalPlan.getTitle()).isEqualTo("Європа"); // Title від User A не зберігся
        assertThat(finalPlan.getBudget()).isEqualByComparingTo(new BigDecimal("2500.0")); // Budget від User B зберігся
        assertThat(finalPlan.getVersion()).isEqualTo(1); // Версія оновилась
    }
}

