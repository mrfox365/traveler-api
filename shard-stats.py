import psycopg2
import sys

# --- КОНФІГУРАЦІЯ ---
DB_USER = "postgres"
DB_PASS = "09125689"

# Реєстр завжди на postgres_00
REGISTRY_HOST = "postgres_00"
REGISTRY_PORT = 5432
REGISTRY_DB = "shard_catalog"

def get_registry_connection():
    try:
        return psycopg2.connect(
            host=REGISTRY_HOST,
            port=REGISTRY_PORT,
            database=REGISTRY_DB,
            user=DB_USER,
            password=DB_PASS
        )
    except Exception as e:
        print(f"Failed to connect to Registry ({REGISTRY_HOST}): {e}")
        sys.exit(1)

def parse_jdbc_url(url):
    # Format: jdbc:postgresql://postgres_01:5432/db_4
    # Remove prefix
    clean = url.replace("jdbc:postgresql://", "")
    # Split host:port and db
    parts = clean.split("/")
    host_port = parts[0].split(":")

    host = host_port[0]
    port = int(host_port[1]) if len(host_port) > 1 else 5432
    db_name = parts[1]

    return host, port, db_name

def main():
    print(f"{'SHARD':<6} | {'LOCATION':<15} | {'DB NAME':<8} | {'ROWS':<8} | {'SIZE':<10}")
    print("-" * 60)

    # 1. Отримуємо список шардів з Реєстру
    reg_conn = get_registry_connection()
    reg_cur = reg_conn.cursor()
    reg_cur.execute("SELECT shard_key, jdbc_url FROM shard_mapping ORDER BY shard_key")
    shards = reg_cur.fetchall()
    reg_conn.close()

    total_rows = 0

    # 2. Проходимо по кожному шарду
    for shard_key, jdbc_url in shards:
        host, port, db_name = parse_jdbc_url(jdbc_url)

        row_count = 0
        size_str = "-"
        status = "OK"

        try:
            # Підключаємося до конкретного шарду
            conn = psycopg2.connect(
                host=host, port=port, database=db_name,
                user=DB_USER, password=DB_PASS, connect_timeout=3
            )
            cur = conn.cursor()

            # Рахуємо рядки
            cur.execute("SELECT count(*) FROM travel_plans")
            row_count = cur.fetchone()[0]

            # Дивимося розмір
            cur.execute("SELECT pg_size_pretty(pg_database_size(current_database()))")
            size_str = cur.fetchone()[0]

            conn.close()

        except psycopg2.errors.UndefinedTable:
            # База є, але таблиці немає (порожній шард)
            row_count = 0
            size_str = "Empty"
        except Exception as e:
            status = "ERROR"
            row_count = 0
            size_str = "Unreachable"

        # Вивід
        print(f"{shard_key:<6} | {host:<15} | {db_name:<8} | {str(row_count):<8} | {size_str:<10}")
        total_rows += row_count

    print("-" * 60)
    print(f"TOTAL RECORDS IN CLUSTER: {total_rows}")

if __name__ == "__main__":
    main()