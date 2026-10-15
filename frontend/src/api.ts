let token = ''
export function setToken(value: string) { token = value }
export async function api<T=unknown>(path: string, method='GET', body?: unknown): Promise<T> {
  const response = await fetch('/api'+path, {method, headers:{'Content-Type':'application/json', ...(token ? {Authorization:'Bearer '+token}: {})}, ...(body === undefined ? {} : {body: JSON.stringify(body)})})
  const data = await response.json().catch(()=>({detail:'The server returned an unreadable response.'}))
  if (!response.ok) {
    const detail = Array.isArray(data.detail) ? data.detail.map((x:{msg:string})=>x.msg).join('. ') : data.detail
    throw new Error(detail || `Request failed (${response.status})`)
  }
  return data as T
}
export async function transcribe(audio: Blob): Promise<{text:string}> {
  const response = await fetch('/api/transcribe', {method:'POST', headers:{'Content-Type':audio.type||'audio/wav', ...(token ? {Authorization:'Bearer '+token}: {})}, body:audio})
  const data = await response.json().catch(()=>({detail:'The server returned an unreadable response.'}))
  if (!response.ok) throw new Error(data.detail || `Transcription failed (${response.status})`)
  return data
}
export type User = {id:string;name:string;email:string}
export type Session = {access_token:string;user:User}
export type Project = {id:string;name:string;description:string;status:string;version:number;task_count:number;completed_count:number;created_at:string}
export type Task = {id:string;project_id:string;project_name:string;title:string;description:string;status:string;priority:string;due_at:string|null;version:number}
export type Reminder = {id:string;title:string;task_id:string|null;task_title:string|null;remind_at:string;state:string}
export type Activity = {id:string;entity_type:string;action:string;detail:Record<string,unknown>;created_at:string}
export type Run = {id:string;conversation_id:string;input:string;status:string;steps:Record<string,unknown>[];results:{action:string;data:unknown;step:number}[];question:null|{kind:string;message:string;action?:string;affected_tasks?:number;options?:{id:string;label:string;detail:string}[]};error:string|null;reply?:string|null;started_at:string;finished_at:string|null}
export type Note = {id:string;title:string;content:string;project_name:string;created_at:string;sources:{title:string;url:string}[]}
export type Analytics = {total_tasks:number;completed_tasks:number;pending_tasks:number;overdue_tasks:number;active_projects:number;task_statuses:{status:string;total:number}[];project_statuses:{status:string;total:number}[]}
export type Config = {planner:{provider:string;model:string;configured:boolean};research:{provider:string;model:string;configured:boolean};mcp:string;timezone:string;laptop?:{gmail:boolean;folders:string[]}}
export const date = (value?:string|null, short=false) => value ? new Intl.DateTimeFormat('en-IN',{timeZone:'Asia/Kolkata',day:'numeric',month:'short',...(short?{}:{hour:'2-digit',minute:'2-digit'})}).format(new Date(value)) : 'No deadline'
export const label = (value:string) => value.replaceAll('_',' ').replace(/^./,c=>c.toUpperCase())
export const isActive = (run?:Run) => !!run && ['queued','planning','executing'].includes(run.status)
