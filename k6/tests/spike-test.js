/**
 * ============================================================================
 * SPIKE TEST
 * ============================================================================
 * Мета: Перевірити поведінку системи при раптових сплесках навантаження.
 */

import { sleep } from 'k6';
import {
    checkHealth,
    createTravelPlan,
    getTravelPlan,
    addLocation,
    deleteTravelPlan,
} from '../utils/api-client.js';
import {
    generateTravelPlan,
    generateLocation,
} from '../utils/data-generator.js';

// ============================================================================
// НАЛАШТУВАННЯ СПЛЕСКІВ (SPIKE)
// ============================================================================

export const options = {
    scenarios: {
        spike_test: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                // 1. Тиша перед бурею (перевіряємо базовий стан)
                { duration: '10s', target: 10 },

                // 2. SPIKE! Раптовий стрибок до 500 юзерів за 10 секунд!
                // Це створить величезний тиск на відкриття з'єднань
                { duration: '10s', target: 500 },

                // 3. Утримання піку (дуже коротко)
                { duration: '30s', target: 500 },

                // 4. Раптовий спад (перевірка, як швидко система "видихне")
                { duration: '10s', target: 10 },

                // 5. Відновлення (перевіряємо, чи все повернулось до норми)
                { duration: '30s', target: 10 },
            ],
        },
    },

    thresholds: {
        // Нам важливо бачити, чи були помилки під час удару
        'http_req_failed': ['rate<0.05'], // Допускаємо до 5% помилок при такому ударі
        'http_req_duration': ['p(95)<2000'],
    },
};

// ============================================================================
// СЦЕНАРІЙ
// ============================================================================

export default function () {
    // Простий сценарій: Створити -> Додати локацію -> Прочитати -> Видалити
    // Мінімальний think time, щоб створити максимальний тиск

    const planData = generateTravelPlan();
    planData.title = `Spike VU-${__VU}`;

    const plan = createTravelPlan(planData);

    if (!plan) {
        // Якщо сервер "ліг", чекаємо довше перед повтором
        sleep(1);
        return;
    }

    const planId = plan.id;

    const locationData = generateLocation();
    addLocation(planId, locationData);

    getTravelPlan(planId);

    deleteTravelPlan(planId);

    // Випадкова пауза 0.1-0.5с (дуже агресивно)
    sleep(Math.random() * 0.4 + 0.1);
}