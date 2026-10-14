-- Run these read-only examples in pgAdmin while connected to agentos.
-- The real application binds the authenticated user UUID through psycopg.
-- This educational file selects the explicit demo account for readability.

-- 1. SELECT + JOIN: pending tasks and their projects.
SELECT p.name AS project, t.title, t.priority, t.due_at
FROM tasks t
JOIN projects p ON p.id=t.project_id AND p.user_id=t.user_id
JOIN users u ON u.id=t.user_id
WHERE u.email='demo@agentos.local' AND t.status<>'completed'
ORDER BY t.due_at NULLS LAST;

-- 2. GROUP BY and COUNT.
SELECT t.status, count(*) AS total
FROM tasks t JOIN users u ON u.id=t.user_id
WHERE u.email='demo@agentos.local'
GROUP BY t.status ORDER BY t.status;

-- 3. LEFT JOIN and conditional aggregation.
SELECT p.name, count(t.id) AS total,
       count(t.id) FILTER (WHERE t.status='completed') AS completed
FROM projects p JOIN users u ON u.id=p.user_id
LEFT JOIN tasks t ON t.project_id=p.id AND t.user_id=p.user_id
WHERE u.email='demo@agentos.local'
GROUP BY p.id ORDER BY p.name;

-- 4. VIEW with an explicit ownership filter.
SELECT v.project,v.title,v.priority,v.due_at
FROM pending_task_summary v JOIN users u ON u.id=v.user_id
WHERE u.email='demo@agentos.local';

-- 5. Real database constraint definitions.
SELECT t.relname AS table_name,c.conname,pg_get_constraintdef(c.oid) AS definition
FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid
WHERE t.relname IN ('projects','tasks','reminders')
ORDER BY t.relname,c.conname;

-- 6. Index definitions. A small demo dataset can legitimately use sequential scans.
SELECT tablename,indexname,indexdef FROM pg_indexes
WHERE schemaname='public' AND tablename IN ('projects','tasks','reminders');

-- 7. Audit entries for the demo account.
SELECT a.action,a.entity_type,a.entity_id,a.detail,a.created_at
FROM audit_logs a JOIN users u ON u.id=a.user_id
WHERE u.email='demo@agentos.local' ORDER BY a.created_at DESC LIMIT 20;

-- INSERT, UPDATE and DELETE are demonstrated by the actual forms and AI tools.
-- Their source SQL is in backend/app/services.py.
-- Use the application's "Test rollback" button to demonstrate a deliberately
-- invalid multi-row INSERT without retaining any test records.
