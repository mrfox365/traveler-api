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

if [[ "$HOST" == "postgres_00" ]]; then
    echo "Creating Catalog Database on $HOST..."
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
        CREATE DATABASE shard_catalog;
        GRANT ALL PRIVILEGES ON DATABASE shard_catalog TO $POSTGRES_USER;
EOSQL

    echo "   Creating Catalog Table and Initial Data..."
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "shard_catalog" <<-EOSQL
        CREATE TABLE IF NOT EXISTS shard_mapping (
            shard_key VARCHAR(10) PRIMARY KEY,
            jdbc_url VARCHAR(255) NOT NULL
        );

        -- Початкове наповнення (Вказуємо де спочатку лежать бази)
        INSERT INTO shard_mapping (shard_key, jdbc_url) VALUES
        ('0', 'jdbc:postgresql://postgres_00:5432/db_0'),
        ('1', 'jdbc:postgresql://postgres_00:5432/db_1'),
        ('2', 'jdbc:postgresql://postgres_00:5432/db_2'),
        ('3', 'jdbc:postgresql://postgres_00:5432/db_3'),
        ('4', 'jdbc:postgresql://postgres_01:5432/db_4'),
        ('5', 'jdbc:postgresql://postgres_01:5432/db_5'),
        ('6', 'jdbc:postgresql://postgres_01:5432/db_6'),
        ('7', 'jdbc:postgresql://postgres_01:5432/db_7'),
        ('8', 'jdbc:postgresql://postgres_02:5432/db_8'),
        ('9', 'jdbc:postgresql://postgres_02:5432/db_9'),
        ('a', 'jdbc:postgresql://postgres_02:5432/db_a'),
        ('b', 'jdbc:postgresql://postgres_02:5432/db_b'),
        ('c', 'jdbc:postgresql://postgres_03:5432/db_c'),
        ('d', 'jdbc:postgresql://postgres_03:5432/db_d'),
        ('e', 'jdbc:postgresql://postgres_03:5432/db_e'),
        ('f', 'jdbc:postgresql://postgres_03:5432/db_f')
        ON CONFLICT (shard_key) DO NOTHING;
EOSQL
fi

# Створюємо тільки потрібні бази
for SHARD in $SHARDS; do
  DB_NAME="db_$SHARD"
  echo "Creating database: $DB_NAME on $HOST"
  
  # Виконуємо SQL команду.
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
      CREATE DATABASE $DB_NAME;
      GRANT ALL PRIVILEGES ON DATABASE $DB_NAME TO $POSTGRES_USER;
EOSQL
done

echo "Initialization complete for $HOST"