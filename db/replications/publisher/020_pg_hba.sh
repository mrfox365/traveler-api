#!/usr/bin/env bash
set -euo pipefail

# Дозволяємо підключення для реплікації
echo "host replication rep_user 0.0.0.0/0 md5" >> "${PGDATA}/pg_hba.conf"

# Перезавантажуємо конфігурацію
pg_ctl reload