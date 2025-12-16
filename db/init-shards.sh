#!/bin/bash
set -e

# Визначаємо хостнейм контейнера (postgres_00, postgres_01...)
HOST=$(hostname)

echo "Smart Init: I am running on $HOST"

# Визначаємо, які бази створювати залежно від хоста
case "$HOST" in
  *"postgres_00"*)
    SHARDS="0 1 2 3"
    ;;
  *"postgres_01"*)
    SHARDS="4 5 6 7"
    ;;
  *"postgres_02"*)
    SHARDS="8 9 a b"
    ;;
  *"postgres_03"*)
    SHARDS="c d e f"
    ;;
  *)
    echo "Unknown host: $HOST. Creating nothing."
    exit 0
    ;;
esac

# Створюємо тільки потрібні бази
for SHARD in $SHARDS; do
  DB_NAME="db_$SHARD"
  echo "🛠️  Creating database: $DB_NAME on $HOST"
  
  # Виконуємо SQL команду.
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
      CREATE DATABASE $DB_NAME;
      GRANT ALL PRIVILEGES ON DATABASE $DB_NAME TO $POSTGRES_USER;
EOSQL
done

echo "Initialization complete for $HOST"