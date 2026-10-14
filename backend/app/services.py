"""Application operations written as bound SQL, with explicit transaction boundaries."""
from uuid import uuid4
from fastapi import HTTPException
from psycopg import sql
from psycopg.types.json import Jsonb
from .db import transaction, query

def audit(conn, user, entity, entity_id, action, detail=None):
    conn.execute('INSERT INTO audit_logs(id,user_id,entity_type,entity_id,action,detail) VALUES (%s,%s,%s,%s,%s,%s)',
                 (uuid4(), user, entity, entity_id, action, Jsonb(detail or {})))

def owned(conn, table, record_id, user, lock=False):
    assert table in {'projects','tasks','reminders','conversations','agent_runs'}
    statement = sql.SQL('SELECT * FROM {} WHERE id=%s AND user_id=%s' + (' FOR UPDATE' if lock else '')).format(sql.Identifier(table))
    row = conn.execute(statement, (record_id, user)).fetchone()
    if not row:
        raise HTTPException(404, 'Record not found')
    return row

def create_project(user, data):
    with transaction() as conn:
        project = conn.execute('INSERT INTO projects(id,user_id,name,description) VALUES (%s,%s,%s,%s) RETURNING *',
                               (uuid4(), user, data['name'].strip(), data.get('description', ''))).fetchone()
        task_ids = []
        for title in data.get('tasks', []):
            task = conn.execute('INSERT INTO tasks(id,user_id,project_id,title) VALUES (%s,%s,%s,%s) RETURNING id',
                                (uuid4(), user, project['id'], title.strip())).fetchone()
            task_ids.append(str(task['id']))
            audit(conn, user, 'task', task['id'], 'INSERT', {'title': title, 'transaction': str(project['id'])})
        audit(conn, user, 'project', project['id'], 'INSERT', {'name': project['name'], 'task_ids': task_ids})
        project['task_ids'] = task_ids
        return project

def list_projects(user):
    return query('''SELECT p.*, count(t.id)::int AS task_count,
        count(t.id) FILTER (WHERE t.status='completed')::int AS completed_count
        FROM projects p LEFT JOIN tasks t ON t.project_id=p.id AND t.user_id=p.user_id
        WHERE p.user_id=%s GROUP BY p.id ORDER BY p.created_at DESC''', (user,))

def create_task(user, data):
    with transaction() as conn:
        owned(conn, 'projects', data['project_id'], user)
        row = conn.execute('''INSERT INTO tasks(id,user_id,project_id,title,description,priority,due_at)
            VALUES (%s,%s,%s,%s,%s,%s,%s) RETURNING *''',
            (uuid4(), user, data['project_id'], data['title'].strip(), data.get('description',''), data.get('priority','medium'), data.get('due_at'))).fetchone()
        audit(conn, user, 'task', row['id'], 'INSERT', {'title': row['title']})
        return row

def list_tasks(user, status=None, project_id=None):
    return query('''SELECT t.*, p.name AS project_name FROM tasks t
        JOIN projects p ON p.id=t.project_id AND p.user_id=t.user_id
        WHERE t.user_id=%s AND (%s::text IS NULL OR t.status=%s)
        AND (%s::uuid IS NULL OR t.project_id=%s::uuid)
        ORDER BY (t.status='completed'), t.due_at NULLS LAST, t.created_at DESC''',
        (user, status, status, project_id, project_id))

def update_record(user, table, record_id, data):
    fields = {'projects': {'name','description','status'}, 'tasks': {'title','description','status','priority','due_at'}}
    if table not in fields:
        raise ValueError('Unsupported update')
    values = {k:v for k,v in data.items() if k in fields[table]}
    with transaction() as conn:
        old = owned(conn, table, record_id, user, lock=True)
        if old['version'] != data['version']:
            raise HTTPException(409, 'This record changed. Refresh and try again.')
        if not values:
            return old
        assignments = sql.SQL(', ').join(sql.SQL('{}=%s').format(sql.Identifier(k)) for k in values)
        row = conn.execute(sql.SQL('UPDATE {} SET {}, version=version+1 WHERE id=%s AND user_id=%s RETURNING *').format(
            sql.Identifier(table), assignments), (*values.values(), record_id, user)).fetchone()
        audit(conn, user, table[:-1], record_id, 'UPDATE', {'fields': list(values), 'version': row['version']})
        return row

def delete_record(user, table, record_id, confirmed=False):
    if not confirmed:
        raise HTTPException(400, 'Confirm the affected record before deletion')
    assert table in {'projects','tasks','reminders'}
    with transaction() as conn:
        old = owned(conn, table, record_id, user, lock=True)
        affected = {}
        if table == 'projects':
            affected['tasks'] = conn.execute('SELECT count(*) AS n FROM tasks WHERE project_id=%s AND user_id=%s', (record_id,user)).fetchone()['n']
        conn.execute(sql.SQL('DELETE FROM {} WHERE id=%s AND user_id=%s').format(sql.Identifier(table)), (record_id,user))
        audit(conn, user, table[:-1], record_id, 'DELETE', {'title': old.get('name', old.get('title')), **affected})
        return {'deleted': str(record_id), **affected}

def create_reminder(user, data):
    with transaction() as conn:
        if data.get('task_id'):
            owned(conn, 'tasks', data['task_id'], user)
        row = conn.execute('INSERT INTO reminders(id,user_id,task_id,title,remind_at) VALUES (%s,%s,%s,%s,%s) RETURNING *',
                           (uuid4(),user,data.get('task_id'),data['title'].strip(),data['remind_at'])).fetchone()
        audit(conn,user,'reminder',row['id'],'INSERT',{'title':row['title']})
        return row

def list_reminders(user):
    return query('''SELECT r.*, t.title AS task_title FROM reminders r LEFT JOIN tasks t ON t.id=r.task_id AND t.user_id=r.user_id
        WHERE r.user_id=%s ORDER BY r.remind_at''', (user,))

def dismiss_reminder(user, record_id):
    with transaction() as conn:
        owned(conn,'reminders',record_id,user)
        row=conn.execute("UPDATE reminders SET state='dismissed' WHERE id=%s AND user_id=%s RETURNING *",(record_id,user)).fetchone()
        audit(conn,user,'reminder',record_id,'UPDATE',{'state':'dismissed'})
        return row

def analytics(user):
    summary = query('''SELECT count(*)::int AS total_tasks,
        count(*) FILTER (WHERE status='completed')::int AS completed_tasks,
        count(*) FILTER (WHERE status<>'completed')::int AS pending_tasks,
        count(*) FILTER (WHERE status<>'completed' AND due_at<now())::int AS overdue_tasks
        FROM tasks WHERE user_id=%s''', (user,), one=True)
    summary['active_projects'] = query("SELECT count(*)::int AS n FROM projects WHERE user_id=%s AND status='active'", (user,), one=True)['n']
    summary['task_statuses'] = query('SELECT status,count(*)::int AS total FROM tasks WHERE user_id=%s GROUP BY status ORDER BY status', (user,))
    summary['project_statuses'] = query('SELECT status,count(*)::int AS total FROM projects WHERE user_id=%s GROUP BY status ORDER BY status', (user,))
    return summary

TEMPLATES = {
    'pending_join': {'name':'Pending tasks by project', 'concept':'SELECT · JOIN · ORDER BY', 'sql':
        "SELECT p.name AS project, t.title, t.priority, t.due_at FROM projects p JOIN tasks t ON t.project_id=p.id AND t.user_id=p.user_id WHERE p.user_id=%s AND t.status<>'completed' ORDER BY t.due_at NULLS LAST"},
    'task_counts': {'name':'Task counts by status','concept':'GROUP BY · COUNT','sql':
        'SELECT status, COUNT(*)::int AS total FROM tasks WHERE user_id=%s GROUP BY status ORDER BY status'},
    'project_progress': {'name':'Project completion','concept':'LEFT JOIN · FILTER · Aggregation','sql':
        "SELECT p.name, count(t.id)::int AS tasks, count(t.id) FILTER (WHERE t.status='completed')::int AS completed FROM projects p LEFT JOIN tasks t ON t.project_id=p.id AND t.user_id=p.user_id WHERE p.user_id=%s GROUP BY p.id ORDER BY p.name"},
    'pending_view': {'name':'Pending task view','concept':'VIEW · Ownership filter','sql':
        'SELECT project,title,priority,due_at FROM pending_task_summary WHERE user_id=%s ORDER BY project'},
    'due_reminders': {'name':'Upcoming reminders','concept':'WHERE · Time filtering','sql':
        "SELECT title,remind_at,state FROM reminders WHERE user_id=%s AND state='scheduled' ORDER BY remind_at"},
}

def execute_template(user, template_id):
    if template_id not in TEMPLATES:
        raise HTTPException(404,'Unknown query template')
    return query(TEMPLATES[template_id]['sql'],(user,))

def message(conn, user, conversation_id, role, content):
    conn.execute('INSERT INTO messages(id,user_id,conversation_id,role,content) VALUES (%s,%s,%s,%s,%s)',
                 (uuid4(),user,conversation_id,role,content))

def create_run(user, data):
    with transaction() as conn:
        # Prevent competing active runs from the same user without trusting the client.
        conn.execute('SELECT id FROM users WHERE id=%s FOR UPDATE',(user,))
        if conn.execute("SELECT 1 FROM agent_runs WHERE user_id=%s AND status IN ('queued','planning','executing')",(user,)).fetchone():
            raise HTTPException(409,'Finish or cancel your current command first')
        # A new command replaces an unanswered permission/choice question; nothing from it has run yet.
        for paused in conn.execute("UPDATE agent_runs SET status='cancelled',question=NULL,finished_at=now() WHERE user_id=%s AND status='awaiting_input' RETURNING conversation_id",(user,)).fetchall():
            message(conn,user,paused['conversation_id'],'assistant','Cancelled: a new command was given before this question was answered.')
        conversation_id = data.get('conversation_id')
        if conversation_id:
            owned(conn,'conversations',conversation_id,user)
        else:
            conversation_id = uuid4()
            conn.execute('INSERT INTO conversations(id,user_id,title) VALUES (%s,%s,%s)',(conversation_id,user,data['input'][:80]))
        run = conn.execute('INSERT INTO agent_runs(id,user_id,conversation_id,input) VALUES (%s,%s,%s,%s) RETURNING *',
                           (uuid4(),user,conversation_id,data['input'])).fetchone()
        message(conn,user,conversation_id,'user',data['input'])
        return run

def get_run(user, run_id):
    with transaction() as conn:
        return owned(conn,'agent_runs',run_id,user)

def update_run(run_id, **values):
    allowed = {'status','steps','results','question','error','finished_at'}
    assert set(values).issubset(allowed)
    assignments = sql.SQL(', ').join(sql.SQL('{}=%s').format(sql.Identifier(k)) for k in values)
    params = [Jsonb(v) if k in {'steps','results','question'} and v is not None else v for k,v in values.items()]
    with transaction() as conn:
        conn.execute(sql.SQL('UPDATE agent_runs SET {} WHERE id=%s').format(assignments),(*params,run_id))
