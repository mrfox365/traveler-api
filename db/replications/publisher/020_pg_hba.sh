#!/usr/bin/env bash
set -euo pipefail

# Налаштовуємо PostgreSQL на використання SCRAM-SHA-256 для шифрування паролів
echo "password_encryption = scram-sha-256" >> "${PGDATA}/postgresql.conf"

# Дозволяємо підключення для реплікації (використовуємо змінну)
echo "host replication $REP_USER 0.0.0.0/0 md5" >> "${PGDATA}/pg_hba.conf"

# Перезавантажуємо конфігурацію
pg_ctl reload