import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    vus: 10,
    duration: '30s',
};

export default function () {
    const url = 'http://localhost:8080/api/travel-plans';

    // Тіло запиту
    const payload = JSON.stringify({
        title: 'Shard Test Plan',
        description: 'Testing distribution across 16 shards',
        startDate: '2024-01-01T10:00:00Z',
        endDate: '2024-01-10T10:00:00Z',
        budget: 1000.00,
        currency: 'USD',
        isPublic: true
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };

    // 1. Створення (Write)
    let res = http.post(url, payload, params);

    // Перевірка, що запис пройшов успішно
    let success = check(res, {
        'create status is 201': (r) => r.status === 201 || r.status === 200,
    });

    if (success) {
        // Отримуємо ID створеного плану
        let id = res.json('id');

        // 2. Читання (Read) - перевіряємо, чи маршрутизація працює
        let readRes = http.get(`${url}/${id}`);

        check(readRes, {
            'read status is 200': (r) => r.status === 200,
            'id matches': (r) => r.json('id') === id
        });
    }

    sleep(0.1); // Невелика пауза
}