"""Exercise the real graph and durable PostgreSQL checkpoints with a controlled planner."""
from uuid import uuid4
from unittest.mock import patch
from app import agents
from app.db import pool,transaction,query
from app import services as s

class ControlledPlanner:
    def __init__(self,steps): self.steps=steps
    def with_structured_output(self,*args,**kwargs): return self
    def invoke(self,*args,**kwargs): return agents.Plan(steps=[agents.Operation(**op) for op in self.steps])

def run(user,text,steps):
    row=s.create_run(user,{'input':text})
    with patch.object(agents,'get_model',return_value=ControlledPlanner(steps)):
        agents.process_run(str(row['id']))
    return s.get_run(user,row['id'])

def main():
    pool.open(wait=True); agents.initialize_checkpoints()
    user=uuid4(); run_ids=[]
    try:
        with transaction() as c:
            c.execute('INSERT INTO users(id,email,name,password_hash) VALUES (%s,%s,%s,%s)',(user,f'{user}@graph.local','Graph check','unused'))
        row=run(user,'Create atomic project',[{'action':'create_project','name':'Graph project','tasks':['Diagram','Queries']},{'action':'counts'}]); run_ids.append(str(row['id']))
        assert row['status']=='completed',row
        assert len(row['results'])==2
        project=s.list_projects(user)[0]
        s.create_task(user,{'project_id':project['id'],'title':'Diagram'})
        row=run(user,'Complete Diagram',[{'action':'update_task','task_title':'Diagram','status':'completed'}]); run_ids.append(str(row['id']))
        assert row['status']=='awaiting_input' and row['question']['kind']=='selection',row
        selected=row['question']['options'][0]['id']
        # Rebuild the graph in process_run, proving the checkpoint is durable.
        agents.process_run(str(row['id']),{'selected_id':selected})
        row=s.get_run(user,row['id']); assert row['status']=='completed',row
        row=run(user,'Delete Graph project',[{'action':'delete_project','project_name':'Graph project'}]); run_ids.append(str(row['id']))
        assert row['status']=='awaiting_input' and row['question']['affected_tasks']==3,row
        assert len(s.list_projects(user))==1
        agents.process_run(str(row['id']),{'approved':False})
        assert s.get_run(user,row['id'])['status']=='cancelled'
        assert len(s.list_projects(user))==1
        row=run(user,'Delete Graph project',[{'action':'delete_project','project_name':'Graph project'}]); run_ids.append(str(row['id']))
        agents.process_run(str(row['id']),{'approved':True})
        assert s.get_run(user,row['id'])['status']=='completed'
        assert not s.list_projects(user)
        print('PASS: real LangGraph execution, atomic tools, multi-step results, ambiguity selection, persisted resume, deletion denial and approval.')
    finally:
        with transaction() as c:
            for table in ('agent_runs','messages','conversations','reminders','research_notes','tasks','projects','audit_logs'):
                c.execute(f'DELETE FROM {table} WHERE user_id=%s',(user,))
            c.execute('DELETE FROM users WHERE id=%s',(user,))
            for table in ('checkpoint_writes','checkpoint_blobs','checkpoints'):
                c.execute(f'DELETE FROM {table} WHERE thread_id=ANY(%s)',(run_ids,))
        pool.close()

if __name__=='__main__': main()
