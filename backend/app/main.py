from contextlib import asynccontextmanager
from datetime import datetime, timezone
from uuid import UUID, uuid4
import os
import time
from fastapi import BackgroundTasks, Depends, FastAPI, HTTPException, Query, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from fastapi.encoders import jsonable_encoder
from psycopg import IntegrityError, OperationalError
from psycopg.errors import CheckViolation
from psycopg_pool import PoolTimeout
from . import auth, laptop, models, services as s
from .config import DATABASE_URL, JWT_SECRET, FRONTEND_ORIGIN, PLANNER_PROVIDER, RESEARCH_PROVIDER
from .providers import model_name
from .db import migrate, pool, transaction, query

@asynccontextmanager
async def lifespan(app):
    if not DATABASE_URL or len(JWT_SECRET)<32:
        raise RuntimeError('Configure DATABASE_URL and a JWT_SECRET of at least 32 characters in backend/.env')
    migrate()
    pool.open(wait=True)
    from .agents import initialize_checkpoints
    initialize_checkpoints()
    with transaction() as c:
        c.execute("UPDATE agent_runs SET status='interrupted', error='Backend restarted during execution. Review committed results before submitting a new command.', finished_at=now() WHERE status IN ('queued','planning','executing')")
    yield
    pool.close()

app=FastAPI(title='AgentOS — direct SQL API',version='1.0.0',lifespan=lifespan)
app.add_middleware(CORSMiddleware,allow_origins=[FRONTEND_ORIGIN,'http://127.0.0.1:5173'],allow_methods=['GET','POST','PATCH','DELETE'],allow_headers=['Authorization','Content-Type'])

@app.exception_handler(IntegrityError)
async def integrity_error(request, exc):
    return JSONResponse(status_code=400,content={'detail':'Database constraint rejected the operation. Check required fields and relationships.'})

@app.exception_handler(OperationalError)
@app.exception_handler(PoolTimeout)
async def database_error(request, exc):
    return JSONResponse(status_code=503,content={'detail':'Database unavailable. No success was confirmed. Please try again.'})

@app.get('/api/health')
def health():
    query('SELECT 1 AS connected')
    return {'status':'ok','database':'PostgreSQL','access':'psycopg · parameterized SQL · no ORM'}

@app.post('/api/auth/register',status_code=201)
def register(data:models.Register):
    return auth.register(data)

@app.post('/api/auth/login')
def login(data:models.Login):
    return auth.login(data)

@app.get('/api/auth/me')
def me(user=Depends(auth.current_user)):
    return user

@app.get('/api/config')
def config(user=Depends(auth.current_user)):
    return {'planner':{'provider':PLANNER_PROVIDER,'model':model_name('planner'),
                       'configured':bool(os.getenv('OPENROUTER_API_KEY' if PLANNER_PROVIDER=='openrouter' else 'GROQ_API_KEY'))},
            'research':{'provider':RESEARCH_PROVIDER,'model':model_name('research'),
                        'configured':bool(os.getenv('GROQ_API_KEY' if RESEARCH_PROVIDER=='groq' else 'OPENROUTER_API_KEY'))},
            'mcp':'Bundled read-only Wikipedia MCP server','timezone':'Asia/Kolkata',
            'laptop':{'gmail':laptop.gmail_configured(),'folders':list(laptop.file_roots())}}

@app.get('/api/projects')
def projects(user=Depends(auth.current_user)):
    return s.list_projects(user['id'])

@app.post('/api/projects',status_code=201)
def new_project(data:models.ProjectCreate,user=Depends(auth.current_user)):
    return s.create_project(user['id'],data.model_dump())

@app.patch('/api/projects/{record_id}')
def edit_project(record_id:UUID,data:models.ProjectUpdate,user=Depends(auth.current_user)):
    return s.update_record(user['id'],'projects',record_id,data.model_dump(exclude_unset=True))

@app.delete('/api/projects/{record_id}')
def remove_project(record_id:UUID,confirmed:bool=False,user=Depends(auth.current_user)):
    return s.delete_record(user['id'],'projects',record_id,confirmed)

@app.get('/api/tasks')
def tasks(status:str|None=None,project_id:UUID|None=None,user=Depends(auth.current_user)):
    return s.list_tasks(user['id'],status,project_id)

@app.post('/api/tasks',status_code=201)
def new_task(data:models.TaskCreate,user=Depends(auth.current_user)):
    return s.create_task(user['id'],data.model_dump())

@app.patch('/api/tasks/{record_id}')
def edit_task(record_id:UUID,data:models.TaskUpdate,user=Depends(auth.current_user)):
    return s.update_record(user['id'],'tasks',record_id,data.model_dump(exclude_unset=True))

@app.delete('/api/tasks/{record_id}')
def remove_task(record_id:UUID,confirmed:bool=False,user=Depends(auth.current_user)):
    return s.delete_record(user['id'],'tasks',record_id,confirmed)

@app.get('/api/reminders')
def reminders(user=Depends(auth.current_user)):
    return s.list_reminders(user['id'])

@app.post('/api/reminders',status_code=201)
def new_reminder(data:models.ReminderCreate,user=Depends(auth.current_user)):
    return s.create_reminder(user['id'],data.model_dump())

@app.patch('/api/reminders/{record_id}/dismiss')
def dismiss_reminder(record_id:UUID,user=Depends(auth.current_user)):
    return s.dismiss_reminder(user['id'],record_id)

@app.delete('/api/reminders/{record_id}')
def remove_reminder(record_id:UUID,confirmed:bool=False,user=Depends(auth.current_user)):
    return s.delete_record(user['id'],'reminders',record_id,confirmed)

@app.get('/api/analytics')
def analytics(user=Depends(auth.current_user)):
    return s.analytics(user['id'])

@app.get('/api/db-activity')
def activity(user=Depends(auth.current_user)):
    return query('SELECT * FROM audit_logs WHERE user_id=%s ORDER BY created_at DESC LIMIT 100',(user['id'],))

@app.get('/api/conversations')
def conversations(user=Depends(auth.current_user)):
    return query('SELECT * FROM conversations WHERE user_id=%s ORDER BY created_at DESC',(user['id'],))

@app.get('/api/conversations/{record_id}/messages')
def messages(record_id:UUID,user=Depends(auth.current_user)):
    with transaction() as c:
        s.owned(c,'conversations',record_id,user['id'])
        return c.execute('SELECT * FROM messages WHERE conversation_id=%s AND user_id=%s ORDER BY created_at',(record_id,user['id'])).fetchall()

@app.get('/api/agent-runs')
def runs(user=Depends(auth.current_user)):
    return query('SELECT * FROM agent_runs WHERE user_id=%s ORDER BY started_at DESC LIMIT 100',(user['id'],))

@app.get('/api/agent-runs/{record_id}')
def run(record_id:UUID,user=Depends(auth.current_user)):
    return s.get_run(user['id'],record_id)

@app.post('/api/laptop/gmail')
def connect_gmail(data:models.GmailConnect,user=Depends(auth.current_user)):
    return laptop.connect_gmail(data.address,data.app_password)

@app.post('/api/transcribe')
async def transcribe(request:Request,user=Depends(auth.current_user)):
    from .speech import transcribe
    return await transcribe(await request.body(),request.headers.get('content-type',''))

@app.get('/api/agent-runs/{record_id}/wait')
def wait_run(record_id:UUID,timeout:float=Query(25,ge=1,le=30),user=Depends(auth.current_user)):
    """Long-poll until the run stops working, so agent mode keeps up even in a background browser tab."""
    deadline=time.monotonic()+timeout
    while True:
        row=s.get_run(user['id'],record_id)
        if row['status'] not in ('queued','planning','executing') or time.monotonic()>=deadline:
            return row
        time.sleep(0.4)

@app.post('/api/commands',status_code=202)
def command(data:models.CommandInput,background:BackgroundTasks,user=Depends(auth.current_user)):
    from .agents import process_run, get_model
    get_model('planner')  # Fail before creating a queued run if credentials are missing.
    run=s.create_run(user['id'],data.model_dump())
    background.add_task(process_run,str(run['id']))
    return run

@app.post('/api/agent-runs/{record_id}/resume',status_code=202)
def resume(record_id:UUID,data:models.ResumeInput,background:BackgroundTasks,user=Depends(auth.current_user)):
    from .agents import process_run
    with transaction() as c:
        row=s.owned(c,'agent_runs',record_id,user['id'],lock=True)
        if row['status']!='awaiting_input':
            raise HTTPException(409,'This run is not waiting for input')
        question=row['question'] or {}
        if question.get('kind')=='approval' and data.approved is None:
            raise HTTPException(422,'An approval decision is required')
        if question.get('kind')=='selection' and str(data.selected_id) not in [str(x['id']) for x in question.get('options',[])]:
            raise HTTPException(422,'Choose one of the offered records')
        c.execute("UPDATE agent_runs SET status='executing',question=NULL WHERE id=%s",(record_id,))
    background.add_task(process_run,str(record_id),data.model_dump(mode='json',exclude_none=True))
    return {'run_id':record_id,'status':'executing'}

@app.post('/api/agent-runs/{record_id}/cancel')
def cancel(record_id:UUID,user=Depends(auth.current_user)):
    with transaction() as c:
        row=s.owned(c,'agent_runs',record_id,user['id'],lock=True)
        if row['status']!='awaiting_input':
            raise HTTPException(409,'Only a paused run can be cancelled')
        c.execute("UPDATE agent_runs SET status='cancelled',question=NULL,finished_at=now() WHERE id=%s",(record_id,))
        s.message(c,user['id'],row['conversation_id'],'assistant','Command cancelled. No further steps were executed.')
    return {'status':'cancelled'}

@app.get('/api/research-notes')
def notes(project_id:UUID|None=None,user=Depends(auth.current_user)):
    return query('SELECT n.*,p.name AS project_name FROM research_notes n JOIN projects p ON p.id=n.project_id AND p.user_id=n.user_id WHERE n.user_id=%s AND (%s::uuid IS NULL OR n.project_id=%s::uuid) ORDER BY n.created_at DESC',(user['id'],project_id,project_id))

@app.post('/api/research-notes',status_code=201)
def research(data:models.ResearchInput,user=Depends(auth.current_user)):
    from .research import research_note
    return research_note(user['id'],data.project_id,data.topic)

@app.get('/api/db/templates')
def templates(user=Depends(auth.current_user)):
    return [{'id':key,**value} for key,value in s.TEMPLATES.items()]

@app.post('/api/db/templates/{template_id}/execute')
def execute(template_id:str,user=Depends(auth.current_user)):
    return s.execute_template(user['id'],template_id)

@app.get('/api/db/schema')
def schema(user=Depends(auth.current_user)):
    names=['users','projects','tasks','reminders','conversations','messages','agent_runs','audit_logs','research_notes']
    columns=query('SELECT table_name,column_name,data_type,is_nullable FROM information_schema.columns WHERE table_schema=%s AND table_name=ANY(%s) ORDER BY table_name,ordinal_position',('public',names))
    constraints=query("SELECT c.conrelid::regclass::text AS table_name,c.conname AS name,pg_get_constraintdef(c.oid) AS definition FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid WHERE t.relname=ANY(%s) AND t.relnamespace='public'::regnamespace ORDER BY table_name,name",(names,))
    return {'columns':columns,'constraints':constraints}

@app.post('/api/db/rollback-demo')
def rollback_demo(user=Depends(auth.current_user)):
    name=f'Rollback demonstration {uuid4()}'
    before=s.analytics(user['id'])['total_tasks']
    try:
        s.create_project(user['id'],{'name':name,'tasks':['Valid task','']})
        raise HTTPException(500,'Unexpected constraint behavior')
    except CheckViolation:
        remaining=query('SELECT count(*) AS n FROM projects WHERE user_id=%s AND name=%s',(user['id'],name),one=True)['n']
        after=s.analytics(user['id'])['total_tasks']
        return {'rolled_back':remaining==0 and before==after,'project_rows_remaining':remaining,'tasks_before':before,'tasks_after':after,
                'explanation':'The second task violated CHECK(title). PostgreSQL rolled back the project, first task, and audit entries together.'}
