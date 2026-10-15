import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AgentModeDock, AgentModeToggle, parseChoice, parseDecision, parseWake, useAgentMode } from '../src/agentMode'

type Listener={onresult:((e:{resultIndex:number;results:{isFinal:boolean;0:{transcript:string}}[]})=>void)|null;onend:(()=>void)|null;onerror:unknown;start:()=>void;abort:()=>void;stop:()=>void}
let recognitions:Listener[],spoken:string[],calls:{path:string;method:string;body:Record<string,unknown>}[]
let waits:Record<string,unknown>[]

function say(text:string){const live=recognitions.filter(r=>r.onresult).at(-1)!;act(()=>live.onresult!({resultIndex:0,results:[{isFinal:true,0:{transcript:text}}]}))}
function Harness(){const agent=useAgentMode();return <><AgentModeToggle agent={agent}/><AgentModeDock agent={agent}/></>}

beforeEach(()=>{
  recognitions=[];spoken=[];calls=[];waits=[]
  class MockRecognition{lang='';continuous=false;interimResults=false;onresult=null;onerror=null;onend=null
    constructor(){recognitions.push(this as unknown as Listener)}start=vi.fn();stop=vi.fn();abort=vi.fn()}
  window.SpeechRecognition=MockRecognition as never
  class Utterance{text:string;voice=null;rate=1;onend:(()=>void)|null=null;onerror:(()=>void)|null=null;constructor(t:string){this.text=t}}
  vi.stubGlobal('SpeechSynthesisUtterance',Utterance)
  Object.defineProperty(window,'speechSynthesis',{configurable:true,value:{getVoices:()=>[],cancel:vi.fn(),
    speak:(u:Utterance)=>{spoken.push(u.text.trim());setTimeout(()=>u.onend?.(),0)}}})
  vi.stubGlobal('fetch',vi.fn(async(path:string,options:RequestInit)=>{
    const method=options?.method??'GET',body=options?.body?JSON.parse(String(options.body)):{}
    calls.push({path,method,body})
    let response:unknown={}
    if(path==='/api/commands')response={id:'run-1',conversation_id:'conv-1',input:body.input,status:'queued',steps:[],results:[],question:null,error:null}
    else if(path.includes('/wait'))response=waits.shift()
    else if(path.includes('/resume'))response={status:'executing'}
    return new Response(JSON.stringify(response),{status:200,headers:{'Content-Type':'application/json'}})
  }))
})
afterEach(()=>{delete window.SpeechRecognition;vi.unstubAllGlobals()})

it('understands wake words, decisions and choices the way people say them',()=>{
  expect(parseWake('Hey Agent, check my latest three emails')).toBe('check my latest three emails')
  expect(parseWake('hey agents what time is it')).toBe('what time is it')
  expect(parseWake('hey agent')).toBe('')
  expect(parseWake('I was talking about the agent')).toBeNull()
  expect(parseDecision('yes go ahead')).toBe(true)
  expect(parseDecision('no, do not do that')).toBe(false)
  expect(parseDecision('okay no')).toBe(false)
  expect(parseDecision('what is this')).toBeNull()
  expect(parseChoice('the second one',3)).toBe(1)
  expect(parseChoice('last',3)).toBe(2)
  expect(parseChoice('fifth',3)).toBeNull()
})

it('listens for the wake word, asks permission aloud, accepts a spoken yes and reads the answer',async()=>{
  const base={id:'run-1',conversation_id:'conv-1',input:'check my latest 3 emails',steps:[{action:'read_emails'}],results:[],error:null}
  waits=[{...base,status:'awaiting_input',question:{kind:'approval',action:'read_emails',message:'Read your 3 latest Gmail messages?'}},
         {...base,status:'completed',question:null,reply:'You have three emails. Amazon says your order shipped.'}]
  render(<QueryClientProvider client={new QueryClient()}><Harness/></QueryClientProvider>)
  await userEvent.click(screen.getByRole('button',{name:/agent mode/i}))
  await waitFor(()=>expect(screen.getByText('Listening for “Hey Agent”')).toBeInTheDocument())

  say('so I was thinking about lunch')
  expect(calls.some(c=>c.path==='/api/commands')).toBe(false)

  say('Hey Agent, check my latest 3 emails')
  await waitFor(()=>expect(screen.getByText('Waiting for your permission')).toBeInTheDocument())
  expect(calls.find(c=>c.path==='/api/commands')?.body.input).toBe('check my latest 3 emails')
  expect(spoken.join(' ')).toContain('I need your permission. Read your 3 latest Gmail messages? Say yes or no.')
  expect(screen.getByRole('button',{name:/allow/i})).toBeInTheDocument()

  say('yes go ahead')
  await waitFor(()=>expect(spoken.join(' ')).toContain('Amazon says your order shipped.'))
  expect(calls.find(c=>c.path.includes('/resume'))?.body).toEqual({approved:true})
  await waitFor(()=>expect(screen.getByText('Listening for “Hey Agent”')).toBeInTheDocument())
  expect(screen.getByText('You have three emails. Amazon says your order shipped.')).toBeInTheDocument()
})

it('a spoken no denies the permission and nothing is read',async()=>{
  const base={id:'run-1',conversation_id:'conv-1',input:'open youtube',steps:[],results:[],error:null}
  waits=[{...base,status:'awaiting_input',question:{kind:'approval',action:'open_website',message:'Open https://www.youtube.com in your browser?'}},
         {...base,status:'cancelled',question:null,reply:'Okay, I did not do that.'}]
  render(<QueryClientProvider client={new QueryClient()}><Harness/></QueryClientProvider>)
  await userEvent.click(screen.getByRole('button',{name:/agent mode/i}))
  say('hey agent open youtube')
  await waitFor(()=>expect(screen.getByText('Waiting for your permission')).toBeInTheDocument())
  say('no')
  await waitFor(()=>expect(spoken.at(-1)).toBe('Okay, I did not do that.'))
  expect(calls.find(c=>c.path.includes('/resume'))?.body).toEqual({approved:false})
})

it('falls back clearly when the browser cannot listen',async()=>{
  delete window.SpeechRecognition
  render(<QueryClientProvider client={new QueryClient()}><Harness/></QueryClientProvider>)
  await userEvent.click(screen.getByRole('button',{name:/agent mode/i}))
  await waitFor(()=>expect(screen.getByText(/Use Google Chrome or Microsoft Edge/)).toBeInTheDocument())
})
