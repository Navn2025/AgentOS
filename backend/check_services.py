"""Real PostgreSQL checks; all generated records are cleaned up afterward."""
from uuid import uuid4
from datetime import datetime,timezone
from psycopg.errors import CheckViolation, ForeignKeyViolation
from fastapi import HTTPException
from app.db import pool, transaction, query
from app import services as s

def main():
    pool.open(wait=True)
    a,b=uuid4(),uuid4()
    try:
        with transaction() as c:
            for user in (a,b):
                c.execute('INSERT INTO users(id,email,name,password_hash) VALUES (%s,%s,%s,%s)',(user,f'{user}@test.local','Check','unused'))
        p=s.create_project(a,{'name':'Verified project','tasks':['ER diagram','SQL queries']})
        assert len(s.list_tasks(a))==2 and len(p['task_ids'])==2
        assert not s.list_projects(b)
        t=s.list_tasks(a)[0]
        try:
            s.update_record(b,'tasks',t['id'],{'version':1,'status':'completed'})
            raise AssertionError('Cross-user update succeeded')
        except HTTPException as e:
            assert e.status_code==404
        s.update_record(a,'tasks',t['id'],{'version':1,'status':'completed'})
        try:
            s.update_record(a,'tasks',t['id'],{'version':1,'status':'pending'})
            raise AssertionError('Stale update succeeded')
        except HTTPException as e:
            assert e.status_code==409
        before=len(s.list_projects(a))
        logs=query('SELECT count(*) AS n FROM audit_logs WHERE user_id=%s',(a,),one=True)['n']
        try:
            s.create_project(a,{'name':'Must roll back','tasks':['Valid title','']})
            raise AssertionError('Invalid task succeeded')
        except CheckViolation:
            pass
        assert len(s.list_projects(a))==before
        assert query('SELECT count(*) AS n FROM audit_logs WHERE user_id=%s',(a,),one=True)['n']==logs
        with transaction() as c:
            try:
                with c.transaction():
                    c.execute('INSERT INTO tasks(id,user_id,project_id,title) VALUES (%s,%s,%s,%s)',(uuid4(),b,p['id'],'Wrong owner'))
                raise AssertionError('Composite ownership constraint failed')
            except ForeignKeyViolation:
                pass
        assert s.analytics(a)['completed_tasks']==1
        assert len(s.execute_template(a,'pending_join'))==1
        assert not s.execute_template(b,'pending_join')
        reminder=s.create_reminder(a,{'title':'Verified reminder','task_id':t['id'],'remind_at':datetime.now(timezone.utc)})
        assert len(s.list_reminders(a))==1 and not s.list_reminders(b)
        assert s.dismiss_reminder(a,reminder['id'])['state']=='dismissed'
        try:
            s.delete_record(a,'reminders',reminder['id'],False)
            raise AssertionError('Unconfirmed reminder deletion succeeded')
        except HTTPException as e: assert e.status_code==400
        s.delete_record(a,'reminders',reminder['id'],True)
        assert not s.list_reminders(a)
        print('PASS: atomic create, audit rollback, cross-user protection, ownership FK, optimistic locking, JOIN, aggregation, reminder create/dismiss/confirmed deletion.')
    finally:
        with transaction() as c:
            for table in ('agent_runs','messages','conversations','reminders','research_notes','tasks','projects','audit_logs'):
                c.execute(f'DELETE FROM {table} WHERE user_id IN (%s,%s)',(a,b))
            c.execute('DELETE FROM users WHERE id IN (%s,%s)',(a,b))
        pool.close()

if __name__=='__main__':
    main()
