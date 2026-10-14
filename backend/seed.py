"""Explicit, idempotent demo seeding. Does not replace existing accounts or records."""
from datetime import datetime,timedelta,timezone
from uuid import uuid4
from app.db import pool,transaction,query,migrate
from app.auth import hashing
from app import services as s

def seed_account(email,name,with_projects):
    if query('SELECT id FROM users WHERE email=%s',(email,),one=True):
        print(f'Skipped existing account: {email}')
        return
    user=uuid4()
    with transaction() as c:
        c.execute('INSERT INTO users(id,email,name,password_hash) VALUES (%s,%s,%s,%s)',(user,email,name,hashing.hash('AgentOS2026!')))
    if with_projects:
        examples=[('DBMS Mini Project','From relational design to a confident live demonstration.',
            ['Design the ER diagram','Write JOIN and aggregation queries','Verify transaction rollback','Prepare the project report','Rehearse the live demonstration']),
            ('Personal Portfolio','A small home on the web for the work I am proud of.',
            ['Sketch the homepage','Build the projects section','Write the about page']),
            ('Learning Journal','Keep useful ideas, notes, and small discoveries together.',
            ['Review PostgreSQL constraints','Explore LangGraph workflows'])]
        now=datetime.now(timezone.utc)
        index=0
        for title,description,titles in examples:
            project=s.create_project(user,{'name':title,'description':description,'tasks':titles})
            for task in s.list_tasks(user,project_id=project['id']):
                status='completed' if index in (0,3,5,8) else 'in_progress' if index==2 else 'pending'
                due=now+timedelta(days=index-2)
                s.update_record(user,'tasks',task['id'],{'version':1,'status':status,'priority':'high' if index in (1,2) else 'medium','due_at':due})
                index+=1
        s.create_reminder(user,{'title':'Rehearse the DBMS demonstration','remind_at':now+timedelta(hours=2)})
        s.create_reminder(user,{'title':'Check tomorrow’s submission checklist','remind_at':now+timedelta(hours=5)})
    else:
        s.create_project(user,{'name':'Reviewer private project','description':'Use this account to demonstrate ownership isolation.','tasks':['Private task']})
    print(f'Created demo account: {email}')

def main():
    migrate();pool.open(wait=True)
    try:
        seed_account('demo@agentos.local','Navne',True)
        seed_account('reviewer@agentos.local','Reviewer',False)
    finally:pool.close()

if __name__=='__main__':main()
