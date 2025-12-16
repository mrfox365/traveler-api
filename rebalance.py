import psycopg2
import sys
import json
import time
import os

# --- КОНФІГУРАЦІЯ ---
DB_USER = "postgres"
DB_PASS = "09125689"
MAPPING_FILE = "/config/mapping.json" # Шлях всередині контейнера

# Визначаємо хости (всередині Docker мережі)
NODES = {
    "postgres_00": {"host": "postgres_00", "port": 5432},
    "postgres_01": {"host": "postgres_01", "port": 5432},
    "postgres_02": {"host": "postgres_02", "port": 5432},
    "postgres_03": {"host": "postgres_03", "port": 5432},
}

def get_connection(node_name, db_name):
    conf = NODES[node_name]
    conn = psycopg2.connect(
        host=conf["host"], port=conf["port"], database=db_name, user=DB_USER, password=DB_PASS
    )
    conn.autocommit = True
    return conn

def update_mapping_file(db_name, new_url):
    print(f"Updating mapping.json for {db_name}...")
    with open(MAPPING_FILE, 'r') as f:
        data = json.load(f)

    # Знаходимо ключ за назвою БД (наприклад, "db_a" -> "a")
    shard_key = None
    for k, v in data.items():
        if f"/{db_name}" in v:
            shard_key = k
            break

    if not shard_key:
        raise Exception(f"Shard key not found for {db_name}")

    data[shard_key] = new_url

    with open(MAPPING_FILE, 'w') as f:
        json.dump(data, f, indent=2)
    print("Mapping updated.")

def rebalance(db_name, target_node):
    # 1. Знаходимо поточний вузол (Source)
    with open(MAPPING_FILE, 'r') as f:
        mapping = json.load(f)

    source_url = None
    shard_key = None
    for k, v in mapping.items():
        if f"/{db_name}" in v:
            source_url = v
            shard_key = k
            break

    if not source_url:
        print(f"Database {db_name} not found in mapping")
        return

    # Парсимо хост з URL (jdbc:postgresql://postgres_02:5432/db_a)
    source_node = source_url.split("//")[1].split(":")[0]

    if source_node == target_node:
        print("⚠Source and Target are the same. Nothing to do.")
        return

    print(f"STARTING REBALANCE: {db_name} from {source_node} -> {target_node}")

    src_conn = get_connection(source_node, db_name)
    tgt_admin_conn = get_connection(target_node, "postgres") # Для створення БД

    # A. Створюємо пусту БД на Target
    try:
        print(f"   Creating database {db_name} on {target_node}...")
        tgt_admin_conn.cursor().execute(f"CREATE DATABASE {db_name}")
    except psycopg2.errors.DuplicateDatabase:
        print("   Database already exists, skipping creation.")
    tgt_admin_conn.close()

    tgt_conn = get_connection(target_node, db_name)

    # B. Створюємо схему (таблиці) на Target
    # У реальному житті краще pg_dump -s, тут спрощено для лаби
    print("   Applying schema to target...")
    schema_sql = """
    CREATE TABLE IF NOT EXISTS travel_plans (id UUID PRIMARY KEY, budget NUMERIC(38, 2), created_at TIMESTAMP(6) WITH TIME ZONE, currency VARCHAR(255), description VARCHAR(255), end_date TIMESTAMP(6) WITH TIME ZONE, is_public BOOLEAN NOT NULL, start_date TIMESTAMP(6) WITH TIME ZONE, title VARCHAR(255), updated_at TIMESTAMP(6) WITH TIME ZONE, version INTEGER);
    CREATE TABLE IF NOT EXISTS locations (id UUID PRIMARY KEY, address VARCHAR(255), arrival_date TIMESTAMP(6) WITH TIME ZONE, budget NUMERIC(38, 2), created_at TIMESTAMP(6) WITH TIME ZONE, departure_date TIMESTAMP(6) WITH TIME ZONE, latitude DOUBLE PRECISION, longitude DOUBLE PRECISION, name VARCHAR(255), notes VARCHAR(255), version INTEGER, visit_order INTEGER, travel_plan_id UUID REFERENCES travel_plans(id) ON DELETE CASCADE);
    """
    tgt_conn.cursor().execute(schema_sql)

    # C. Налаштування Логічної Реплікації
    pub_name = f"pub_{db_name}_move"
    sub_name = f"sub_{db_name}_move"

    print("   1. Creating PUBLICATION on Source...")
    try:
        src_conn.cursor().execute(f"CREATE PUBLICATION {pub_name} FOR ALL TABLES")
    except psycopg2.errors.DuplicateObject:
        pass

    print("   2. Creating SUBSCRIPTION on Target...")
    # Важливо: Target повинен знати як достукатися до Source (внутрішній докер хост)
    conn_str = f"host={source_node} port=5432 dbname={db_name} user={DB_USER} password={DB_PASS}"
    try:
        tgt_conn.cursor().execute(f"CREATE SUBSCRIPTION {sub_name} CONNECTION '{conn_str}' PUBLICATION {pub_name}")
    except psycopg2.errors.DuplicateObject:
        pass

    # D. Чекаємо синхронізації
    print("   Waiting for initial sync...")
    time.sleep(5) # Спрощено. В реальності треба перевіряти pg_stat_subscription

    # E. Ексклюзивне блокування (Cutover)
    print("   3. LOCKING Source tables (Stop Writes)...")
    src_conn.autocommit = False # Починаємо транзакцію
    src_cur = src_conn.cursor()
    # Блокуємо на запис, але дозволяємо читання, поки реплікація добігає
    src_cur.execute("LOCK TABLE travel_plans, locations IN EXCLUSIVE MODE")

    print("   Syncing final changes...")
    time.sleep(2) # Даємо час добігти останнім байтам

    # F. Промоція Target
    print("   4. Detaching Subscription (Promote)...")
    tgt_conn.cursor().execute(f"DROP SUBSCRIPTION {sub_name}")
    src_conn.cursor().execute(f"DROP PUBLICATION {pub_name}") # Видаляємо публ в тій же транзакції (або ні)

    # G. Оновлення мапінгу
    new_jdbc_url = f"jdbc:postgresql://{target_node}:5432/{db_name}"
    update_mapping_file(db_name, new_jdbc_url)

    # H. "Отруєння" старого джерела (Fencing)
    print("   6. Revoking permissions on Source...")
    # Відкликаємо права connect для користувача (або перейменовуємо таблиці)
    # Щоб програма отримала помилку і перечитала конфіг
    src_cur.execute("ALTER TABLE travel_plans RENAME TO travel_plans_moved")
    src_cur.execute("ALTER TABLE locations RENAME TO locations_moved")

    src_conn.commit() # Фіксуємо блокування/перейменування
    print("REBALANCE COMPLETE!")

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: python rebalance.py <db_name> <target_node>")
        print("Example: python rebalance.py db_a postgres_00")
        sys.exit(1)

    rebalance(sys.argv[1], sys.argv[2])