-- Цей скрипт має помилку (CRATE замість CREATE)
-- Він повинен викликати Rollback у всіх базах
CRATE TABLE test_fail (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50)
);