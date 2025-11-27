/**
 * ============================================================================
 * ENDURANCE TEST
 * ============================================================================
 * Мета: Перевірка стабільності системи при тривалому навантаженні.
 * Пошук витоків пам'яті та вичерпання ресурсів.
 */

import { sleep } from 'k6';
import {
    checkHealth,
    createTravelPlan,
    getTravelPlan,
    addLocation,
    updateTravelPlan,
    deleteTravelPlan,
} from '../utils/api-client.js';
import {
    generateTravelPlan,
    generateLocation,
} from '../utils/data-generator.js';

export const options = {
    scenarios: {
        endurance_test: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '2m', target: 100 },   // Плавний розігрів
                { duration: '30m', target: 100 },  // 30 ХВИЛИН стабільного навантаження
                { duration: '2m', target: 0 },    // Охолодження
            ],
        },
    },

    // Якщо помилок стає забагато - перериваємо тест, бо це вже провал
    thresholds: {
        'http_req_failed': ['rate<0.01'], // Помилок має бути менше 1%
        'http_req_duration': ['p(95)<500'], // Час відповіді має бути стабільним
    },
};

export default function () {
    // 1. Health Check
    checkHealth();

    // 2. Create
    const planData = generateTravelPlan();
    planData.title = `Endurance VU-${__VU}`;

    const plan = createTravelPlan(planData);

    if (!plan) {
        sleep(5); // Якщо помилка, чекаємо довше
        return;
    }

    // 3. Add Location
    const locationData = generateLocation();
    addLocation(plan.id, locationData);

    // 4. Read
    getTravelPlan(plan.id);

    // 5. Update
    const updateData = { ...planData, version: 0, budget: 3000 };
    updateTravelPlan(plan.id, updateData);

    // 6. Delete (Важливо видаляти, щоб база не розрослася до гігабайт за 30 хв)
    deleteTravelPlan(plan.id);

    // Стабільна пауза, щоб тримати ритм
    sleep(1);
}