"""Connect to your existing PostgreSQL/pgAdmin server without storing credentials in code."""
import getpass
from pathlib import Path
import secrets
from urllib.parse import quote
import psycopg
from psycopg import sql
from dotenv import dotenv_values,set_key

def main():
    env=Path(__file__).resolve().parent/'.env'
    host=input('PostgreSQL host [localhost]: ').strip() or 'localhost'
    port=int(input('PostgreSQL port [5432]: ').strip() or '5432')
    user=input('PostgreSQL username [postgres]: ').strip() or 'postgres'
    password=getpass.getpass('PostgreSQL password: ')
    database=input('Database name [agentos]: ').strip() or 'agentos'
    with psycopg.connect(host=host,port=port,user=user,password=password,dbname='postgres',autocommit=True) as c:
        if not c.execute('SELECT 1 FROM pg_database WHERE datname=%s',(database,)).fetchone():
            c.execute(sql.SQL('CREATE DATABASE {}').format(sql.Identifier(database)))
    url=f'postgresql://{quote(user,safe="")}:{quote(password,safe="")}@{host}:{port}/{quote(database,safe="")}'
    with psycopg.connect(url) as c:
        tables={r[0] for r in c.execute("SELECT tablename FROM pg_tables WHERE schemaname='public'")}
        if tables and 'schema_migrations' not in tables:
            raise SystemExit('Database has unrelated existing tables. Choose an empty database. No migration was applied.')
    values=dotenv_values(env) if env.exists() else {}
    set_key(env,'DATABASE_URL',url)
    defaults={'JWT_SECRET':secrets.token_hex(32),'PLANNER_PROVIDER':'groq','RESEARCH_PROVIDER':'groq',
        'OPENROUTER_MODEL':'nvidia/nemotron-3-super-120b-a12b:free','GROQ_MODEL':'qwen/qwen3.8-27b','GROQ_PLANNER_MODEL':'openai/gpt-oss-120b',
        'OPENROUTER_API_KEY':'','GROQ_API_KEY':'','FRONTEND_ORIGIN':'http://localhost:5173'}
    for key,value in defaults.items():
        if key not in values:set_key(env,key,value)
    from app.db import migrate
    migrate(url)
    print(f'Connected, configured and migrated database {database}. Refresh Databases in pgAdmin.')

if __name__=='__main__':main()
