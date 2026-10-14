from uuid import uuid4
from unittest.mock import patch
from psycopg import OperationalError
from fastapi.testclient import TestClient
from app.main import app
from app.db import transaction,pool

def main():
    users=[]
    with TestClient(app) as client:
        try:
            assert client.get('/api/projects').status_code==401
            for name in ['First','Second']:
                data={'name':name,'email':f'{uuid4()}@check.local','password':'VerifiedPassword123'}
                r=client.post('/api/auth/register',json=data)
                assert r.status_code==201, r.text
                users.append(r.json()['user']['id'])
                if name=='First':
                    a={'Authorization':'Bearer '+r.json()['access_token']}
                    assert client.post('/api/auth/register',json=data).status_code==409
                    assert client.post('/api/auth/login',json={'email':data['email'],'password':data['password']}).status_code==200
                else:
                    b={'Authorization':'Bearer '+r.json()['access_token']}
            p=client.post('/api/projects',headers=a,json={'name':'API verified','tasks':['Check SQL']})
            assert p.status_code==201,p.text
            p=p.json()
            assert client.get('/api/projects',headers=b).json()==[]
            assert client.patch('/api/projects/'+p['id'],headers=b,json={'version':1,'status':'archived'}).status_code==404
            assert client.delete('/api/projects/'+p['id'],headers=a).status_code==400
            assert client.post('/api/db/rollback-demo',headers=a).json()['rolled_back']
            assert len(client.get('/api/db/schema',headers=a).json()['columns'])>40
            assert client.post('/api/db/templates/task_counts/execute',headers=a).json()[0]['total']==1
            assert client.post('/api/tasks',headers=a,json={'project_id':p['id'],'title':'Bad date','due_at':'2026-10-15T12:00:00'}).status_code==422
            from app.agents import Plan,Operation
            class Planner:
                def with_structured_output(self,*args,**kwargs): return self
                def invoke(self,*args,**kwargs): return Plan(steps=[Operation(action='delete_project',project_id=p['id'])])
            with patch('app.agents.get_model',return_value=Planner()):
                submitted=client.post('/api/commands',headers=a,json={'input':'Delete API verified'})
            assert submitted.status_code==202,submitted.text
            run_id=submitted.json()['id']
            paused=client.get('/api/agent-runs/'+run_id,headers=a).json()
            assert paused['status']=='awaiting_input',paused
            assert client.get('/api/agent-runs/'+run_id,headers=b).status_code==404
            assert client.post('/api/agent-runs/'+run_id+'/resume',headers=a,json={}).status_code==422
            assert client.post('/api/agent-runs/'+run_id+'/resume',headers=a,json={'approved':False}).status_code==202
            assert client.get('/api/agent-runs/'+run_id,headers=a).json()['status']=='cancelled'
            assert client.post('/api/agent-runs/'+run_id+'/resume',headers=a,json={'approved':True}).status_code==409
            assert len(client.get('/api/projects',headers=a).json())==1
            assert client.delete('/api/projects/'+p['id']+'?confirmed=true',headers=a).status_code==200
            assert client.get('/api/tasks',headers=a).json()==[]
            with patch.object(pool,'connection',side_effect=OperationalError('Synthetic connection failure')):
                disconnected=client.get('/api/analytics',headers=a)
            assert disconnected.status_code==503 and 'No success was confirmed' in disconnected.json()['detail']
            print('PASS: auth, uniqueness, ownership, confirmation, schema, rollback, timezone validation, cascades, command API, durable approval, denial, duplicate-resume rejection, database failure reporting.')
        finally:
            with transaction() as c:
                for table in ('agent_runs','messages','conversations','reminders','research_notes','tasks','projects','audit_logs'):
                    c.execute(f'DELETE FROM {table} WHERE user_id=ANY(%s::uuid[])',(users,))
                c.execute('DELETE FROM users WHERE id=ANY(%s::uuid[])',(users,))
                if 'run_id' in locals():
                    for table in ('checkpoint_writes','checkpoint_blobs','checkpoints'):
                        c.execute(f'DELETE FROM {table} WHERE thread_id=%s',(run_id,))

if __name__=='__main__':
    main()
