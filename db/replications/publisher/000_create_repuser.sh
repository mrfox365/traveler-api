#!/usr/bin/env bash
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE USER $REP_USER WITH REPLICATION LOGIN ENCRYPTED PASSWORD '$REP_PASS';
EOSQL