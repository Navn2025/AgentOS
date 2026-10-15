import { beforeEach, expect, it, vi } from 'vitest'
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { EarEvents } from '../src/voiceEngine'

const ear={kind:'whisper' as const,pause:vi.fn(),resume:vi.fn(),pushStart:vi.fn(),pushEnd:vi.fn(),destroy:vi.fn()}
let events:EarEvents
vi.mock('../src/voiceEngine',()=>({
  createWhisperEar:vi.fn(async(e:EarEvents)=>{events=e;return ear}),
  createBrowserEar:vi.fn(()=>{throw new Error('browser engine should not be used')}),
}))
const { AgentModeDock, AgentModeToggle, parseWake, useAgentMode } = await import('../src/agentMode')

let calls:{path:string;body:Record<string,unknown>}[]
function Harness(){const agent=useAgentMode();return <><AgentModeToggle agent={agent}/><AgentModeDock agent={agent}/></>}
beforeEach(()=>{
  calls=[];Object.values(ear).forEach(v=>typeof v==='function'&&v.mockClear())
  vi.stubGlobal('SpeechSynthesisUtterance',class{text:string;onend:(()=>void)|null=null;onerror=null;constructor(t:string){this.text=t}})
  Object.defineProperty(window,'speechSynthesis',{configurable:true,value:{getVoices:()=>[],cancel:vi.fn(),speak:(u:{onend:()=>void})=>setTimeout(()=>u.onend?.(),0)}})
  vi.stubGlobal('fetch',vi.fn(async(path:string,options:RequestInit)=>{
    const body=options?.body?JSON.parse(String(options.body)):{};calls.push({path,body})
    const response=path==='/api/commands'?{id:'run-1',conversation_id:'c',input:body.input,status:'queued',steps:[],results:[],question:null,error:null}
      :{id:'run-1',conversation_id:'c',input:'x',status:'completed',steps:[],results:[],question:null,error:null,reply:'All done.'}
    return new Response(JSON.stringify(response),{status:200,headers:{'Content-Type':'application/json'}})
  }))
})

it('accepts the wake word at the start of a sentence, as Whisper often drops the "hey"',()=>{
  expect(parseWake('Agent, check my latest three emails.')).toBe('check my latest three emails.')
  expect(parseWake('AgentOS open YouTube')).toBe('open YouTube')
  expect(parseWake('the travel agent called me')).toBeNull()
})

it('uses Whisper, ignores background talk, and push-to-talk needs no wake word',async()=>{
  render(<QueryClientProvider client={new QueryClient()}><Harness/></QueryClientProvider>)
  await userEvent.click(screen.getByRole('button',{name:/agent mode/i}))
  await waitFor(()=>expect(screen.getByText('Whisper · noise filtered')).toBeInTheDocument())
  await waitFor(()=>expect(screen.getByText('Listening for “Hey Agent”')).toBeInTheDocument())

  act(()=>events.onText('so what are we having for lunch today',false))
  expect(calls.some(c=>c.path==='/api/commands')).toBe(false)

  fireEvent.keyDown(window,{code:'Space'})
  expect(ear.pushStart).toHaveBeenCalledTimes(1)
  expect(screen.getByText('Listening… release to send')).toBeInTheDocument()
  fireEvent.keyUp(window,{code:'Space'})
  expect(ear.pushEnd).toHaveBeenCalledTimes(1)

  act(()=>events.onText('check my latest three emails',true))
  await waitFor(()=>expect(calls.find(c=>c.path==='/api/commands')?.body.input).toBe('check my latest three emails'))
  await waitFor(()=>expect(screen.getByText('All done.')).toBeInTheDocument())
  expect(ear.pause).toHaveBeenCalled()  // the microphone is paused while the agent speaks, so it never hears itself
})

it('Space typed into a text box is not push-to-talk',async()=>{
  render(<QueryClientProvider client={new QueryClient()}><Harness/><textarea aria-label="notes"/></QueryClientProvider>)
  await userEvent.click(screen.getByRole('button',{name:/agent mode/i}))
  await waitFor(()=>expect(screen.getByText('Whisper · noise filtered')).toBeInTheDocument())
  fireEvent.keyDown(screen.getByLabelText('notes'),{code:'Space'})
  expect(ear.pushStart).not.toHaveBeenCalled()
})
