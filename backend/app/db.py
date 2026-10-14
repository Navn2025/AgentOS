from contextlib import contextmanager
from pathlib import Path
import psycopg
from psycopg.rows import dict_row
from psycopg_pool import ConnectionPool
from .config import DATABASE_URL, ROOT

pool = ConnectionPool(DATABASE_URL or 'postgresql://localhost/agentos', open=False,
                      min_size=1, max_size=10, kwargs={'row_factory': dict_row})

def migrate(url=None):
    with psycopg.connect(url or DATABASE_URL, row_factory=dict_row) as conn:
        # Serialize migration runners. DDL and migration ledger commit together.
        conn.execute('SELECT pg_advisory_xact_lock(724193801)')
        conn.execute('CREATE TABLE IF NOT EXISTS schema_migrations (version text PRIMARY KEY, applied_at timestamptz NOT NULL DEFAULT now())')
        for path in sorted((ROOT / 'migrations').glob('*.sql')):
            if not conn.execute('SELECT 1 FROM schema_migrations WHERE version=%s', (path.name,)).fetchone():
                conn.execute(path.read_text(encoding='utf-8'))
                conn.execute('INSERT INTO schema_migrations(version) VALUES (%s)', (path.name,))

@contextmanager
def transaction():
    with pool.connection() as conn:
        with conn.transaction():
            yield conn

def query(sql, params=(), one=False):
    with transaction() as conn:
        cursor = conn.execute(sql, params)
        return cursor.fetchone() if one else cursor.fetchall()

if __name__ == '__main__':
    migrate()
    print('SQL migrations applied successfully.')
