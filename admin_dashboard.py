import streamlit as st
import pandas as pd
import psycopg2
import os
import subprocess

# Налаштування підключення до Реєстру
CATALOG_CONN = "dbname='shard_catalog' user='postgres' password='09125689' host='postgres_00'"

st.set_page_config(page_title="Shard Manager", layout="wide")
st.title("🛡️ Distributed DB Admin Panel")

# --- 1. Відображення Реєстру ---
st.header("1. Shard Registry (Catalog)")
try:
    conn = psycopg2.connect(CATALOG_CONN)
    df = pd.read_sql("SELECT * FROM shard_mapping ORDER BY shard_key", conn)
    conn.close()

    # Гарна табличка
    st.dataframe(df, use_container_width=True)

    # Графік розподілу шардів по нодах
    df['node'] = df['jdbc_url'].apply(lambda x: x.split('//')[1].split(':')[0])
    st.bar_chart(df['node'].value_counts())

except Exception as e:
    st.error(f"Could not connect to Registry: {e}")
    st.stop()

# --- 2. Управління ---
st.header("2. Actions")
col1, col2 = st.columns(2)

with col1:
    st.subheader("Move Shard (Rebalance)")
    shard_to_move = st.selectbox("Select Database (Shard)", df['jdbc_url'].apply(lambda x: x.split('/')[-1]).unique())
    target_node = st.selectbox("Target Node", ["postgres_00", "postgres_01", "postgres_02", "postgres_03"])

    if st.button("🚀 Start Rebalance"):
        with st.spinner("Rebalancing in progress..."):
            # Викликаємо ваш існуючий скрипт!
            result = subprocess.run(
                ["python", "rebalance.py", shard_to_move, target_node],
                capture_output=True, text=True
            )
            if result.returncode == 0:
                st.success("Success!")
                st.code(result.stdout)
            else:
                st.error("Failed")
                st.code(result.stderr)