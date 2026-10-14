import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import App from '../src/App'
import { RunDetail } from '../src/pages/Workspace'
import type { Run } from '../src/api'

let projects: Record<string,unknown>[], tasks: Record<string,unknown>[]
let calls:{path:string;method:string;body:Record<string,unknown>}[]
const config={planner:{provider:'openrouter',model:'nvidia/nemotron-3-super-120b-a12b:free',configured:false},research:{provider:'groq',model:'llama-3.3-70b-versatile',configured:false}}
beforeEach(()=>{
 projects=[{id:'project-1',name:'DBMS Mini Project',description:'A working demonstration',status:'active',version:1,task_count:1,completed_count:0}]
 tasks=[{id:'task-1',project_id:'project-1',project_name:'DBMS Mini Project',title:'ER diagram',status:'pending',priority:'high',version:1,due_at:null}]
 calls=[]
 vi.stubGlobal('fetch',vi.fn(async(path:string,options:RequestInit)=>{
  const method=options?.method??'GET',body=options?.body?JSON.parse(String(options.body)):{}
  calls.push({path,method,body});let response:unknown=[];let status=200
  if(path.includes('/auth/login')){if(body.password==='bad-password'){response={detail:'Incorrect email or password'};status=401}else response={access_token:'test-token',user:{id:'test-user',name:'Navne',email:'demo@agentos.local'}}}
  else if(path.includes('/auth/register'))response={access_token:'test-token',user:{id:'test-user',name:body.name,email:body.email}}
  else if(path==='/api/projects'&&method==='GET')response=projects
  else if(path==='/api/projects'&&method==='POST'){response={...body,id:'project-new',status:'active',version:1,task_count:(body.tasks??[]).length,completed_count:0};projects=[response as Record<string,unknown>,...projects]}
  else if(path.startsWith('/api/projects/')&&method==='DELETE'){projects=projects.filter(p=>!path.includes(String(p.id)));response={deleted:'project-1'}}
  else if(path==='/api/tasks')response=tasks
  else if(path.startsWith('/api/tasks/')&&method==='PATCH'){tasks=tasks.map(t=>({...t,status:body.status,version:2}));response=tasks[0]}
  else if(path==='/api/analytics')response={total_tasks:tasks.length,completed_tasks:tasks.filter(t=>t.status==='completed').length,pending_tasks:tasks.filter(t=>t.status!=='completed').length,overdue_tasks:0,active_projects:projects.length,task_statuses:[],project_statuses:[]}
  else if(path==='/api/config')response=config
  else if(path==='/api/commands'){status=503;response={detail:'Add OPENROUTER_API_KEY to backend/.env and restart the backend to enable the planner.'}}
  else if(path==='/api/db/schema')response={columns:[],constraints:[]}
  else if(path==='/api/db/templates')response=[{id:'pending_join',name:'Pending tasks by project',concept:'SELECT · JOIN',sql:'SELECT title FROM tasks WHERE user_id=%s'}]
  else if(path.includes('/api/db/templates/pending_join/execute'))response=[{project:'DBMS Mini Project',title:'ER diagram'}]
  else if(path==='/api/db/rollback-demo')response={rolled_back:true,project_rows_remaining:0,tasks_before:1,tasks_after:1,explanation:'All related inserts rolled back.'}
  return new Response(JSON.stringify(response),{status,headers:{'Content-Type':'application/json'}})
 }))
})
function mount(){const client=new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}});render(<QueryClientProvider client={client}><MemoryRouter><App/></MemoryRouter></QueryClientProvider>);return userEvent.setup()}
async function signIn(){const user=mount();await user.type(screen.getByLabelText('Email address'),'demo@agentos.local');await user.type(screen.getByLabelText('Password'),'AgentOS2026!');await user.click(screen.getByRole('button',{name:'Sign in'}));await screen.findByRole('heading',{name:'Hello, Navne.'});return user}

describe('Offline workspace acceptance checks',()=>{
 it('shows failed login truthfully and accepts valid credentials',async()=>{
  const user=mount();await user.type(screen.getByLabelText('Email address'),'demo@agentos.local');await user.type(screen.getByLabelText('Password'),'bad-password');await user.click(screen.getByRole('button',{name:'Sign in'}));expect(await screen.findByRole('alert')).toHaveTextContent('Incorrect email');expect(screen.queryByRole('heading',{name:'Hello, Navne.'})).not.toBeInTheDocument()
 })
 it('creates an account and opens the workspace',async()=>{
  const user=mount();await user.click(screen.getByRole('button',{name:'Create an account'}));await user.type(screen.getByLabelText('Your name'),'Student');await user.type(screen.getByLabelText('Email address'),'student@test.local');await user.type(screen.getByLabelText('Password'),'Example123!');await user.click(screen.getByRole('button',{name:'Create account'}));expect(await screen.findByRole('heading',{name:'Hello, Student.'})).toBeInTheDocument()
 })
 it('sends a project and initial task list in one operation',async()=>{
  const user=await signIn();await user.click(screen.getByRole('link',{name:'Projects'}));await user.click(screen.getByRole('button',{name:'New project'}));const dialog=screen.getByRole('dialog');await user.type(within(dialog).getByLabelText('Project name'),'Submission demo');await user.type(within(dialog).getByRole('textbox',{name:/Initial tasks/}),'Diagram\nQueries');await user.click(within(dialog).getByRole('button',{name:'Save project'}));await screen.findByRole('button',{name:'Submission demo'});expect(calls.find(c=>c.path==='/api/projects'&&c.method==='POST')?.body.tasks).toEqual(['Diagram','Queries'])
 })
 it('completes a task and refreshes its displayed state',async()=>{
  const user=await signIn();await user.click(screen.getByRole('link',{name:/^Tasks/}));await user.click(await screen.findByRole('button',{name:'Complete ER diagram'}));expect(await screen.findByRole('button',{name:'Reopen ER diagram'})).toBeInTheDocument();expect(calls.find(c=>c.method==='PATCH')?.body).toEqual({version:1,status:'completed'})
 })
 it('does not delete before explicit confirmation',async()=>{
  const user=await signIn();await user.click(screen.getByRole('link',{name:'Projects'}));await user.click(screen.getByRole('button',{name:'Delete DBMS Mini Project'}));expect(screen.getByRole('dialog')).toHaveTextContent('This cannot be undone');expect(calls.some(c=>c.method==='DELETE')).toBe(false);await user.click(screen.getByRole('button',{name:'Keep project'}));expect(calls.some(c=>c.method==='DELETE')).toBe(false)
  await user.click(screen.getByRole('button',{name:'Delete DBMS Mini Project'}));await user.click(screen.getByRole('button',{name:'Delete permanently'}));await waitFor(()=>expect(calls.filter(c=>c.method==='DELETE')).toHaveLength(1));expect(calls.find(c=>c.method==='DELETE')?.path).toContain('confirmed=true')
 })
 it('reports missing AI credentials without claiming command success',async()=>{
  const user=await signIn();await user.click(screen.getByRole('link',{name:/AI Workspace/}));await user.type(screen.getByRole('textbox',{name:'Your command'}),'Show my tasks');await user.click(screen.getByRole('button',{name:'Send command'}));expect(await screen.findByRole('alert')).toHaveTextContent('OPENROUTER_API_KEY');expect(screen.queryByText('CONFIRMED')).not.toBeInTheDocument()
 })
 it('runs approved SQL and displays the rollback demonstration',async()=>{
  const user=await signIn();await user.click(screen.getByRole('link',{name:'Database Explorer'}));await user.click(screen.getByRole('button',{name:'SQL playground'}));await user.click(await screen.findByRole('button',{name:'Run query'}));expect(await screen.findByRole('cell',{name:'ER diagram'})).toBeInTheDocument();await user.click(screen.getByRole('button',{name:'Test rollback'}));expect(await screen.findByText('Complete rollback verified')).toBeInTheDocument()
 })
 it('approval controls send explicit decisions',async()=>{
  const onResume=vi.fn(),user=userEvent.setup();const run:Run={id:'run-1',conversation_id:'conversation-1',input:'Delete project',status:'awaiting_input',steps:[],results:[],question:{kind:'approval',message:'Delete DBMS?',affected_tasks:3},error:null,started_at:'2026-10-14T00:00:00Z',finished_at:null};render(<RunDetail run={run} onResume={onResume}/>);expect(onResume).not.toHaveBeenCalled();await user.click(screen.getByRole('button',{name:'Deny'}));expect(onResume).toHaveBeenCalledWith({approved:false})
 })
})
