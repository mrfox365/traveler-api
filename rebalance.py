import psycopg2
import sys
import time
import os

# --- КОНФІГУРАЦІЯ ---
DB_USER = "postgres"
DB_PASS = "09125689"

# Визначаємо хости
NODES = {
    "postgres_00": {"host": "postgres_00", "port": 5432},
    "postgres_01": {"host": "postgres_01", "port": 5432},
    "postgres_02": {"host": "postgres_02", "port": 5432},
    "postgres_03": {"host": "postgres_03", "port": 5432},
}

# Конфіг каталогу (Реєстру)
CATALOG_NODE = {"host": "postgres_00", "port": 5432, "db": "shard_catalog"}

def get_connection(node_name, db_name):
    """Створює з'єднання до будь-якої бази даних."""
    if node_name not in NODES:
        raise ValueError(f"Unknown node: {node_name}")

    conf = NODES[node_name]
    conn = psycopg2.connect(
        host=conf["host"], port=conf["port"], database=db_name, user=DB_USER, password=DB_PASS
    )
    conn.autocommit = True
    return conn

def get_catalog_connection():
    """Створює з'єднання до бази реєстру."""
    return psycopg2.connect(
        host=CATALOG_NODE["host"], port=CATALOG_NODE["port"],
        database=CATALOG_NODE["db"], user=DB_USER, password=DB_PASS
    )

def update_mapping_db(shard_key, new_url):
    """Оновлює запис у базі реєстру."""
    print(f"Updating Database Registry for shard '{shard_key}'...")
    conn = get_catalog_connection()
    cur = conn.cursor()

    sql = "UPDATE shard_mapping SET jdbc_url = %s WHERE shard_key = %s"
    cur.execute(sql, (new_url, shard_key))
    conn.commit()
    conn.close()
    print("Registry updated.")

def rebalance(db_name, target_node):
    print(f"Preparing rebalance for {db_name} -> {target_node}...")

    # 1. Отримуємо поточний URL з БД Реєстру
    conn = get_catalog_connection()
    cur = conn.cursor()

    # Витягуємо shard_key з імені бази
    shard_key = db_name.split("_")[1]

    cur.execute("SELECT jdbc_url FROM shard_mapping WHERE shard_key = %s", (shard_key,))
    result = cur.fetchone()
    conn.close()

    if not result:
        print(f"Shard {shard_key} not found in registry")
        return

    source_url = result[0]
    # source_url = jdbc:postgresql://postgres_02:5432/db_a
    # Парсимо хост: postgres_02
    source_node = source_url.split("//")[1].split(":")[0]

    if source_node == target_node:
        print("Source and Target are the same. Nothing to do.")
        return

    print(f"STARTING MIGRATION: {source_node} -> {target_node}")

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
    conn_str = f"host={source_node} port=5432 dbname={db_name} user={DB_USER} password={DB_PASS}"
    try:
        tgt_conn.cursor().execute(f"CREATE SUBSCRIPTION {sub_name} CONNECTION '{conn_str}' PUBLICATION {pub_name}")
    except psycopg2.errors.DuplicateObject:
        pass

    # D. Чекаємо синхронізації
    print("   Waiting for initial sync...")
    time.sleep(5)

    # E. Ексклюзивне блокування (Cutover)
    print("   3. LOCKING Source tables (Stop Writes)...")
    src_conn.autocommit = False # Починаємо транзакцію
    src_cur = src_conn.cursor()
    src_cur.execute("LOCK TABLE travel_plans, locations IN EXCLUSIVE MODE")

    print("   Syncing final changes...")
    time.sleep(2)

    # F. Промоція Target
    print("   4. Detaching Subscription (Promote)...")
    tgt_conn.cursor().execute(f"DROP SUBSCRIPTION {sub_name}")
    # Важливо: publication видаляємо в тій же транзакції, якщо це можливо, або пізніше
    # Тут ми просто залишаємо її, щоб не ламати транзакцію блокування

    # G. Оновлення мапінгу в БД
    new_jdbc_url = f"jdbc:postgresql://{target_node}:5432/{db_name}"
    update_mapping_db(shard_key, new_jdbc_url)

    # H. "Отруєння" старого джерела (Fencing)
    print("   6. Revoking permissions on Source...")
    src_cur.execute("ALTER TABLE travel_plans RENAME TO travel_plans_moved")
    src_cur.execute("ALTER TABLE locations RENAME TO locations_moved")

    src_conn.commit() # Фіксуємо блокування/перейменування

    # Прибираємо сміття на джерелі (вже поза транзакцією)
    src_conn.autocommit = True
    try:
        src_conn.cursor().execute(f"DROP PUBLICATION {pub_name}")
    except:
        pass

    print("REBALANCE COMPLETE!")

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: python rebalance.py <db_name> <target_node>")
        sys.exit(1)

    rebalance(sys.argv[1], sys.argv[2])