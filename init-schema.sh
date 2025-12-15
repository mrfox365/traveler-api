#!/bin/sh
set -e

# SQL DDL - створюємо зміну через EOF для безпеки
SQL=$(cat <<EOF
CREATE TABLE IF NOT EXISTS travel_plans (
    id UUID PRIMARY KEY,
    budget NUMERIC(38, 2),
    created_at TIMESTAMP(6) WITH TIME ZONE,
    currency VARCHAR(255),
    description VARCHAR(255),
    end_date TIMESTAMP(6) WITH TIME ZONE,
    is_public BOOLEAN NOT NULL,
    start_date TIMESTAMP(6) WITH TIME ZONE,
    title VARCHAR(255),
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    version INTEGER
);

CREATE TABLE IF NOT EXISTS locations (
    id UUID PRIMARY KEY,
    address VARCHAR(255),
    arrival_date TIMESTAMP(6) WITH TIME ZONE,
    budget NUMERIC(38, 2),
    created_at TIMESTAMP(6) WITH TIME ZONE,
    departure_date TIMESTAMP(6) WITH TIME ZONE,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    name VARCHAR(255),
    notes VARCHAR(255),
    version INTEGER,
    visit_order INTEGER,
    travel_plan_id UUID REFERENCES travel_plans(id) ON DELETE CASCADE
);
EOF
)

# Функція очікування готовності бази
wait_for_db() {
    host="$1"
    port="$2"
    echo "Waiting for postgres at $host:$port..."
    # Чекаємо поки pg_isready поверне 0 (успіх)
    until pg_isready -h "$host" -p "$port" -U "postgres"; do
        echo "Postgres at $host is unavailable - sleeping"
        sleep 2
    done
    echo "Postgres at $host is UP!"
}

echo "Starting schema initialization..."

HOSTS="postgres_00 postgres_01 postgres_02 postgres_03"

# Чекаємо на всі бази
for host in $HOSTS; do
    wait_for_db "$host" "5432"
done

# Список шардів (простий рядок)
SHARDS="0 1 2 3 4 5 6 7 8 9 a b c d e f"

# Проходимо по всіх шардах і створюємо таблиці
for shard in $SHARDS; do
    # Визначаємо хост для шарду через case
    case "$shard" in
        0|1|2|3) host="postgres_00" ;;
        4|5|6|7) host="postgres_01" ;;
        8|9|a|b) host="postgres_02" ;;
        c|d|e|f) host="postgres_03" ;;
    esac

    db_name="db_$shard"
    echo "Initializing $db_name on $host..."

    # Виконуємо SQL
    PGPASSWORD="09125689" psql -h "$host" -U postgres -d "$db_name" -c "$SQL"
done

echo "Schema initialization completed successfully!"