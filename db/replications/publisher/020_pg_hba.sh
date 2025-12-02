#!/usr/bin/env bash
set -euo pipefail
# Дозволити subscriber підключатися по паролю до всіх баз під користувачем rep_user
echo "host all rep_user 0.0.0.0/0 md5" >> "${PGDATA}/pg_hba.conf"

pg_ctl reload