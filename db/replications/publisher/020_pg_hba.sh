#!/usr/bin/env bash
set -euo pipefail

# Дозволяємо підключення для реплікації (використовуємо змінну)
echo "host replication all 0.0.0.0/0 trust" >> "${PGDATA}/pg_hba.conf"
echo "host all all 0.0.0.0/0 trust" >> "${PGDATA}/pg_hba.conf"

# Перезавантажуємо конфігурацію
pg_ctl reload