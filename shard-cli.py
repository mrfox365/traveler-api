import psycopg2
import sys
import argparse
import json
import os

AM_I_IN_DOCKER = os.environ.get("AM_I_IN_DOCKER")

if AM_I_IN_DOCKER:
    print("Running inside Docker network mode")
    # Всередині Docker мережі хости - це імена сервісів, а порт завжди 5432
    SHARDS = [
        {"host": "postgres_00", "port": 5432, "dbs": ["db_0", "db_1", "db_2", "db_3"]},
        {"host": "postgres_01", "port": 5432, "dbs": ["db_4", "db_5", "db_6", "db_7"]},
        {"host": "postgres_02", "port": 5432, "dbs": ["db_8", "db_9", "db_a", "db_b"]},
        {"host": "postgres_03", "port": 5432, "dbs": ["db_c", "db_d", "db_e", "db_f"]},
    ]
else:
    print("Running in Host mode (localhost)")
    # Зовні Docker ми стукаємо на localhost і різні зовнішні порти
    SHARDS = [
        {"host": "localhost", "port": 5432, "dbs": ["db_0", "db_1", "db_2", "db_3"]},
        {"host": "localhost", "port": 5433, "dbs": ["db_4", "db_5", "db_6", "db_7"]},
        {"host": "localhost", "port": 5434, "dbs": ["db_8", "db_9", "db_a", "db_b"]},
        {"host": "localhost", "port": 5435, "dbs": ["db_c", "db_d", "db_e", "db_f"]},
    ]

USER = "postgres"
PASS = "09125689"

def apply_script(file_path):
    if not os.path.exists(file_path):
        print(f"File not found: {file_path}")
        return

    with open(file_path, 'r') as f:
        sql = f.read()

    connections = []
    cursors = []

    print(f"Starting distributed transaction on 16 shards...")

    try:
        # 1. Відкриваємо з'єднання до ВСІХ баз (Phase 1)
        for node in SHARDS:
            for db_name in node["dbs"]:
                conn = psycopg2.connect(
                    host=node["host"], port=node["port"],
                    database=db_name, user=USER, password=PASS
                )
                conn.autocommit = False # Вимикаємо авто-коміт
                connections.append(conn)

                cur = conn.cursor()
                cursors.append(cur)

                print(f"   Executing on {db_name}...")
                cur.execute(sql) # Виконуємо, але НЕ комітимо

        # 2. Якщо ми тут - помилок не було. Комітимо ВСЮДИ (Phase 2)
        print("All shards executed successfully. Committing...")
        for conn in connections:
            conn.commit()
            conn.close()

        print("🎉 DONE. Script applied to all shards.")

    except Exception as e:
        print(f"\nERROR detected: {e}")
        print("⚠Rolling back ALL transactions...")

        # 3. Відкат (Rollback)
        for conn in connections:
            try:
                conn.rollback()
                conn.close()
            except:
                pass
        print("System state restored (nothing changed).")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python shard-cli.py <script.sql>")
    else:
        apply_script(sys.argv[1])