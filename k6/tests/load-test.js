/**
 * ============================================================================
 * LOAD / STRESS TEST
 * ============================================================================
 * Мета: Визначити продуктивність API під навантаженням.
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
    verifyPlanDeleted,
    thinkTime,
} from '../utils/api-client.js';
import {
    generateTravelPlan,
    generateLocation,
} from '../utils/data-generator.js';

// ============================================================================
// НАЛАШТУВАННЯ НАВАНТАЖЕННЯ (STRESS TEST)
// ============================================================================

export const options = {
    // Ми використовуємо "ramping-vus", щоб поступово збільшувати навантаження
    // і знайти точку, де система почне "ламатися".
    scenarios: {
        stress_test: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '1m', target: 10 },  // Розігрів: плавно до 10 користувачів
                { duration: '2m', target: 50 },  // Навантаження: до 50 користувачів
                { duration: '2m', target: 100 }, // Стрес: до 100 користувачів (пік)
                { duration: '1m', target: 100 }, // Утримання піку
                { duration: '1m', target: 0 },   // Охолодження
            ],
            gracefulRampDown: '30s',
        },
    },

    // Поріг успіху (SLA)
    // Якщо помилок більше 1% або 95% запитів повільніші за 1с — тест провалено
    thresholds: {
        'http_req_duration': ['p(95)<1000'], // 95% запитів мають бути швидші за 1000ms
        'http_req_failed': ['rate<0.01'],    // Помилок менше 1%
    },
};

// ============================================================================
// СЦЕНАРІЙ (Те саме, що і в smoke-test, але швидше)
// ============================================================================

export default function () {
    // 1. Health Check (швидка перевірка)
    checkHealth();

    // 2. Створення плану
    const planData = generateTravelPlan();
    planData.title = `Load Test Plan VU-${__VU}`; // Унікальне ім'я для кожного юзера

    const plan = createTravelPlan(planData);
    if (!plan) return; // Якщо не вдалося створити, перериваємо ітерацію

    const planId = plan.id;

    // 3. Додавання локації (інтенсивна операція запису)
    const locationData = generateLocation();
    addLocation(planId, locationData);

    // 4. Читання плану (операція читання)
    getTravelPlan(planId);

    // 5. Оновлення (оптимістичне блокування)
    // Для швидкості в лоад тесті ми пропускаємо складну логіку re-fetch,
    // просто пробуємо оновити з відомою версією (0 -> 1)
    // Якщо виникне конфлікт - це теж результат навантаження.
    const updateData = { ...planData, version: 0, budget: 9999 };
    updateTravelPlan(planId, updateData);

    // 6. Видалення (очистка)
    deleteTravelPlan(planId);

    // Коротка пауза між діями (менша, ніж у smoke test, щоб створити тиск)
    sleep(1);
}