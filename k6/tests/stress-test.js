/**
 * ============================================================================
 * STRESS TEST
 * ============================================================================
 * Мета: Знайти межу можливостей системи (Breaking Point).
 */

import { sleep } from 'k6';
import {
    checkHealth,
    createTravelPlan,
    getTravelPlan,
    addLocation,
    updateTravelPlan,
    deleteTravelPlan,
    listTravelPlans,
} from '../utils/api-client.js';
import {
    generateTravelPlan,
    generateLocation,
} from '../utils/data-generator.js';

// ============================================================================
// НАЛАШТУВАННЯ СТРЕС-ТЕСТУ
// ============================================================================

export const options = {
    scenarios: {
        stress_test: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 100 },  // Розминка
                { duration: '1m', target: 500 },   // Серйозне навантаження
                { duration: '1m', target: 1000 },  // ЕКСТРЕМАЛЬНЕ НАВАНТАЖЕННЯ
                { duration: '2m', target: 10000 },  // Намагаємося впасти
                { duration: '2m', target: 10000 },  // Утримання піку
                { duration: '2m', target: 0 },     // Охолодження
            ],
        },
    },

    // Ми не встановлюємо жорсткі thresholds, щоб тест не перервався автоматично.
    // Нам ПОТРІБНО побачити помилки.
    thresholds: {
        'http_req_failed': ['rate<1'], // Просто моніторимо, не перериваємо
    },
};

// ============================================================================
// СЦЕНАРІЙ
// ============================================================================

export default function () {
    // 1. Health Check
    checkHealth();

    // 2. Створення плану
    const planData = generateTravelPlan();
    planData.title = `Stress Test VU-${__VU}`;

    const plan = createTravelPlan(planData);

    // При сильному стресі створення може впасти.
    // Якщо так - просто чекаємо і пробуємо в наступній ітерації.
    if (!plan) {
        sleep(1);
        return;
    }

    const planId = plan.id;

    // 3. Активна робота (запис)
    const locationData = generateLocation();
    addLocation(planId, locationData);

    // 4. Читання
    getTravelPlan(planId);

    // 5. Оновлення (без складної логіки версій для швидкості)
    const updateData = { ...planData, version: 0, budget: 5000 };
    updateTravelPlan(planId, updateData);

    // 6. Видалення
    deleteTravelPlan(planId);

    // Мінімальна пауза, щоб створити максимальний тиск на сервер
    sleep(0.5);
}