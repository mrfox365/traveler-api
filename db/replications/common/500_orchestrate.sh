#!/usr/bin/env bash
set -euo pipefail

run_sql_dir() {
  local dir="$1"
  if [ -d "$dir" ]; then
      echo "Searching scripts in $dir"
      # Спочатку *.sql
      find "$dir" -maxdepth 1 -type f -name "*.sql" | sort | while read -r f; do
        echo "Applying SQL: $f"
        psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f "$f"
      done
      # Потім *.sh
      find "$dir" -maxdepth 1 -type f -name "*.sh" | sort | while read -r f; do
        echo "Running SH: $f"
        bash "$f"
      done
  else
      echo "Directory $dir does not exist."
  fi
}

# 1. Міграції (запускають обидва)
echo "--- Running Migrations ---"
run_sql_dir "/docker-entrypoint-migrations"

# 2. Реплікація
echo "--- Setting up Replication (Role: ${ROLE:-unknown}) ---"

if [ "${ROLE:-}" = "publisher" ]; then
    run_sql_dir "/docker-entrypoint-replications/publisher"
elif [ "${ROLE:-}" = "subscriber" ]; then
    run_sql_dir "/docker-entrypoint-replications/subscriber"
else
    echo "No ROLE defined (or unknown), skipping replication setup."
fi