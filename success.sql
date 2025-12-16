-- Цей скрипт правильний
-- Він має створити таблицю у всіх 16 базах
CREATE TABLE IF NOT EXISTS global_settings (
                                               id SERIAL PRIMARY KEY,
                                               setting_name VARCHAR(100),
    setting_value VARCHAR(100)
    );

INSERT INTO global_settings (setting_name, setting_value)
VALUES ('shard_version', 'v1.0');