/**
 * API Client
 * Допоміжні функції для роботи з Travel Planner API
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';
import { ENDPOINTS, DEFAULT_HEADERS } from '../config/endpoints.js';

// Кастомні метрики
export const errorRate = new Rate('api_errors');
export const conflictRate = new Rate('optimistic_lock_conflicts');

/**
 * Виконує HTTP запит з перевірками та метриками
 */
function makeRequest(method, url, body = null, expectedStatuses = [200], operationType = 'read') {

  const statuses = Array.isArray(expectedStatuses) ? expectedStatuses : [expectedStatuses];

  const params = {
    headers: DEFAULT_HEADERS,
    tags: {
      type: operationType,
      endpoint: url.replace(/\/[0-9a-f-]{36}/g, '/:id'),
    },
    responseCallback: http.expectedStatuses(...statuses),
  };

  let response;
  if (body) {
    response = http.request(method, url, JSON.stringify(body), params);
  } else {
    response = http.request(method, url, null, params);
  }

  const statusCheck = check(response, {
    [`status is one of [${statuses.join(',')}]`]: (r) => statuses.includes(r.status),
  });

  if (!statusCheck) {
    errorRate.add(1);
  } else {
    errorRate.add(0);
  }

  if (response.status === 409) {
    conflictRate.add(1);
  }

  return response;
}

/**
 * Створює новий travel plan
 */
export function createTravelPlan(planData) {
  const response = makeRequest(
      'POST',
      ENDPOINTS.TRAVEL_PLANS,
      planData,
      201,
      'write'
  );

  const success = check(response, {
    'plan created successfully': (r) => r.status === 201,
    'plan has valid UUID': (r) => {
      if (r.status !== 201) return false;
      try {
        const body = JSON.parse(r.body);
        return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(body.id);
      } catch (e) {
        return false;
      }
    },
    // ВИПРАВЛЕНО: Очікуємо версію 0 (так працює ваш Java @Version)
    'plan has version 0': (r) => {
      if (r.status !== 201) return false;
      try {
        const body = JSON.parse(r.body);
        return body.version === 0;
      } catch (e) {
        return false;
      }
    },
  });

  if (response.status !== 201) return null;

  try {
    return JSON.parse(response.body);
  } catch (e) {
    return null;
  }
}

/**
 * Отримує travel plan за ID
 */
export function getTravelPlan(planId) {
  const response = makeRequest(
      'GET',
      ENDPOINTS.TRAVEL_PLAN_BY_ID(planId),
      null,
      200,
      'read'
  );

  if (response.status !== 200) return null;

  try {
    return JSON.parse(response.body);
  } catch (e) {
    return null;
  }
}

/**
 * Перевіряє чи план видалений (очікує 404)
 */
export function verifyPlanDeleted(planId) {
  const response = makeRequest(
      'GET',
      ENDPOINTS.TRAVEL_PLAN_BY_ID(planId),
      null,
      404,
      'read'
  );

  return check(response, {
    'plan is deleted (404)': (r) => r.status === 404,
  });
}

/**
 * Оновлює travel plan
 */
export function updateTravelPlan(planId, updateData) {
  const response = makeRequest(
      'PUT',
      ENDPOINTS.TRAVEL_PLAN_BY_ID(planId),
      updateData,
      [200, 409],
      'write'
  );

  check(response, {
    'plan updated successfully': (r) => r.status === 200,
  });

  if (response.status === 200) {
    return JSON.parse(response.body);
  }

  if (response.status === 409) {
    return { conflict: true, body: JSON.parse(response.body) };
  }

  return null;
}

/**
 * Видаляє travel plan
 */
export function deleteTravelPlan(planId) {
  const response = makeRequest(
      'DELETE',
      ENDPOINTS.TRAVEL_PLAN_BY_ID(planId),
      null,
      204,
      'write'
  );

  return check(response, {
    'plan deleted successfully': (r) => r.status === 204,
  });
}

/**
 * Додає локацію до travel plan
 */
export function addLocation(planId, locationData) {
  const response = makeRequest(
      'POST',
      ENDPOINTS.LOCATIONS_FOR_PLAN(planId),
      locationData,
      201,
      'write'
  );

  const success = check(response, {
    'location created successfully': (r) => r.status === 201,

    // ВИПРАВЛЕНО: використовуємо visit_order замість visitOrder
    'location has visitOrder': (r) => {
      if (r.status !== 201) return false;
      try {
        const body = JSON.parse(r.body);
        // Сервер повертає snake_case
        return body.visit_order >= 1;
      } catch (e) {
        return false;
      }
    },

    // ВИПРАВЛЕНО: використовуємо travel_plan_id замість travelPlanId
    'location linked to plan': (r) => {
      if (r.status !== 201) return false;
      try {
        const body = JSON.parse(r.body);
        // Сервер повертає snake_case
        return body.travel_plan_id === planId;
      } catch (e) {
        return false;
      }
    },
  });

  if (response.status !== 201) return null;

  try {
    return JSON.parse(response.body);
  } catch (e) {
    return null;
  }
}

/**
 * Оновлює локацію (З УРАХУВАННЯМ ВЕРСІЇ)
 * ЗАВДАННЯ 3
 */
export function updateLocation(locationId, updateData) {
  // 1. Спочатку отримуємо актуальну версію через GET
  const getResponse = http.get(ENDPOINTS.LOCATION_BY_ID(locationId), { headers: DEFAULT_HEADERS });

  if (getResponse.status !== 200) {
    console.error(`Failed to fetch location ${locationId} for update`);
    return null;
  }

  const currentLocation = JSON.parse(getResponse.body);

  // 2. Додаємо актуальну версію до даних оновлення
  // Створюємо копію даних, щоб не мутувати вхідний об'єкт
  const dataWithVersion = Object.assign({}, updateData);
  dataWithVersion.version = currentLocation.version;

  // 3. Робимо PUT з версією
  const response = makeRequest(
      'PUT',
      ENDPOINTS.LOCATION_BY_ID(locationId),
      dataWithVersion,
      [200, 409],
      'write'
  );

  check(response, {
    'location updated successfully': (r) => r.status === 200,
  });

  if (response.status === 200) {
    return JSON.parse(response.body);
  }
  return null;
}

/**
 * Видаляє локацію
 */
export function deleteLocation(locationId) {
  const response = makeRequest(
      'DELETE',
      ENDPOINTS.LOCATION_BY_ID(locationId),
      null,
      204,
      'write'
  );

  return check(response, {
    'location deleted successfully': (r) => r.status === 204,
  });
}

/**
 * Отримує список всіх travel plans
 */
export function listTravelPlans() {
  const response = makeRequest(
      'GET',
      ENDPOINTS.TRAVEL_PLANS,
      null,
      200,
      'read'
  );

  check(response, {
    'plans list retrieved': (r) => r.status === 200,
    'response is array': (r) => {
      if (r.status !== 200) return false;
      try {
        // У вас пагінація, тому масив лежить в полі content
        const body = JSON.parse(r.body);
        return Array.isArray(body.content) || Array.isArray(body);
      } catch(e) { return false; }
    },
  });

  if (response.status === 200) {
    return JSON.parse(response.body);
  }
  return null;
}

/**
 * Перевіряє здоров'я API
 */
export function checkHealth() {
  const response = makeRequest(
      'GET',
      ENDPOINTS.HEALTH,
      null,
      200,
      'read'
  );

  // ВИПРАВЛЕНО: Перевіряємо текст "UP", а не JSON
  return check(response, {
    'API is healthy': (r) => r.status === 200,
    'status is UP': (r) => r.body === 'UP',
  });
}

/**
 * Виконує валідаційний тест
 */
export function testValidation(method, url, invalidData) {
  const response = makeRequest(
      method,
      url,
      invalidData,
      400,
      'write'
  );

  return check(response, {
    'validation error returned': (r) => r.status === 400,
    'error message present': (r) => {
      if (r.status !== 400) return false;
      try {
        const body = JSON.parse(r.body);
        // Ваш бекенд повертає поле 'error': 'Validation error'
        return body.error && body.error.includes('Validation');
      } catch(e) { return false; }
    },
  });
}

export function thinkTime(min = 1, max = 3) {
  const duration = Math.random() * (max - min) + min;
  sleep(duration);
}