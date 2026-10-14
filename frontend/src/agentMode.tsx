import { useCallback, useEffect, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Bot, Check, Loader2, Mic, MicOff, ShieldCheck, Square, Volume2, X } from 'lucide-react'
import { api } from './api'
import type { Run } from './api'
import { createBrowserEar, createWhisperEar } from './voiceEngine'
import type { Ear, EarEvents } from './voiceEngine'
import { GmailConnect } from './GmailConnect'
import './agent.css'

export type Phase='off'|'listening'|'command'|'thinking'|'permission'|'choice'|'speaking'

// Speech engines often hear "hey agent" as "hey agents", "hi agent" or "a agent".
const WAKE=/(?:\b(?:hey|hi|hay|hai|ok|okay|a)[\s,]+agent(?:s|os| os)?\b|^\s*agent(?:os| os)?\b)[\s,.!?:-]*/i
const NO=/\b(no|nope|nah|don'?t|do not|deny|cancel|stop|never ?mind|not now|reject)\b/i
const YES=/\b(yes|yeah|yep|yup|allow|allowed|go ahead|sure|ok|okay|do it|proceed|confirm|approve|haan)\b/i
// Ordinals win over numbers so "the second one" means 2, not 1.
const ORDINALS=['first','second','third','fourth','fifth','sixth','seventh','eighth'].map((w,i)=>new RegExp(`\\b(${w}|${i+1}(st|nd|rd|th))\\b`,'i'))
const NUMBERS=['one','two','three','four','five','six','seven','eight'].map((w,i)=>new RegExp(`\\b(${w}|${i+1})\\b`,'i'))
const SLEEP=/^(stop listening|turn off|go to sleep|sleep|goodbye|bye)\b/i

/** Text after the wake word, '' for the wake word alone, or null when it was not said. */
export function parseWake(text:string):string|null {const m=WAKE.exec(text);return m?text.slice(m.index+m[0].length).trim():null}
export function parseDecision(text:string):boolean|null {return NO.test(text)?false:YES.test(text)?true:null}
export function parseChoice(text:string,count:number):number|null {
  if(/\blast\b/i.test(text))return count-1
  const ordinal=ORDINALS.findIndex(pattern=>pattern.test(text))
  const index=ordinal>=0?ordinal:NUMBERS.findIndex(pattern=>pattern.test(text))
  return index>=0&&index<count?index:null
}
export function speakable(text:string) {return text.replace(/[*_#`>|]/g,'').replace(/https?:\/\/\S+/g,'the link').replace(/\s+/g,' ').trim()}
function chunks(text:string) {return speakable(text).match(/[^.!?]+[.!?]*\s*/g)?.reduce<string[]>((all,part)=>{const last=all.length-1;if(last>=0&&(all[last]+part).length<180)all[last]+=part;else all.push(part);return all},[])??[]}
function pickVoice() {
  const voices=window.speechSynthesis?.getVoices()??[]
  return voices.find(v=>/natural/i.test(v.name)&&v.lang.startsWith('en'))??voices.find(v=>v.lang==='en-IN')??
    voices.find(v=>/google.*english/i.test(v.name))??voices.find(v=>v.lang.startsWith('en'))
}

export function useAgentMode() {
  const client=useQueryClient()
  const [phase,setPhaseState]=useState<Phase>('off'),[heard,setHeard]=useState(''),[command,setCommand]=useState('')
  const [run,setRun]=useState<Run|null>(null),[reply,setReply]=useState(''),[error,setError]=useState('')
  const [hearing,setHearing]=useState(false),[engine,setEngine]=useState<Ear['kind']|null>(null),[talking,setTalking]=useState(false)
  const phaseRef=useRef<Phase>('off'),ear=useRef<Ear|null>(null),creating=useRef(false),runRef=useRef<Run|null>(null)
  const conversation=useRef<string|null>(null),commandTimer=useRef<ReturnType<typeof setTimeout>|null>(null)
  const handleRef=useRef<(text:string,direct:boolean)=>void>(()=>{}),speech=useRef(0),disableRef=useRef<()=>void>(()=>{})
  const setPhase=useCallback((value:Phase)=>{phaseRef.current=value;setPhaseState(value)},[])

  const stopListening=useCallback(()=>{ear.current?.pause()},[])
  const startListening=useCallback(()=>{
    const current=phaseRef.current
    if(current==='off'||current==='speaking'||current==='thinking')return
    if(ear.current){ear.current.resume();return}
    if(creating.current)return
    creating.current=true
    const events:EarEvents={onText:(text,direct)=>{setHeard(text);handleRef.current(text,direct)},onHearing:setHearing,
      onError:message=>{setError(message);if(/permission|microphone was found/i.test(message))disableRef.current()}}
    // Whisper + Silero VAD is far more accurate in noise; the browser recognizer is only a fallback.
    createWhisperEar(events).catch((e:Error)=>{
      if(e.name==='NotAllowedError')throw new Error('Microphone permission was denied. Allow the microphone for this site (lock icon in the address bar), then turn agent mode on again.')
      if(e.name==='NotFoundError')throw new Error('No microphone was found.')
      console.warn('Whisper voice engine unavailable, using browser recognition:',e)
      return createBrowserEar(events)
    }).then(created=>{
      creating.current=false
      if(phaseRef.current==='off'){created.destroy();return}
      ear.current=created;setEngine(created.kind)
      if(['speaking','thinking'].includes(phaseRef.current))created.pause()
    }).catch((e:Error)=>{creating.current=false;setError(e.message);setPhase('off')})
  },[setPhase])

  const speak=useCallback((text:string,next:Phase)=>new Promise<void>(resolve=>{
    stopListening();setPhase('speaking')
    const synth=window.speechSynthesis,parts=chunks(text),token=++speech.current
    let finished=false
    // Only the latest utterance may hand control back; Chrome sometimes never fires onend, hence the timer.
    const done=()=>{if(finished)return;finished=true;if(token===speech.current&&phaseRef.current==='speaking'){setPhase(next);startListening()}resolve()}
    if(!synth||!parts.length){done();return}
    synth.cancel();setTimeout(done,text.length*90+4000)
    const voice=pickVoice()
    parts.forEach((part,i)=>{const utterance=new SpeechSynthesisUtterance(part);if(voice)utterance.voice=voice;utterance.rate=1.02
      if(i===parts.length-1){utterance.onend=done;utterance.onerror=done};synth.speak(utterance)})
  }),[setPhase,startListening,stopListening])

  const settle=useCallback(async(current:Run)=>{
    let latest=current
    while(['queued','planning','executing'].includes(latest.status)){
      if(phaseRef.current==='off')return
      try{latest=await api<Run>(`/agent-runs/${latest.id}/wait?timeout=25`)}catch(e){await speak((e as Error).message,'listening');return}
      setRun(latest);runRef.current=latest
    }
    if(phaseRef.current==='off')return
    const question=latest.question
    if(latest.status==='awaiting_input'&&question?.kind==='approval'){
      await speak(`I need your permission. ${question.message} Say yes or no.`,'permission')
    } else if(latest.status==='awaiting_input'&&question?.kind==='selection'){
      const options=(question.options??[]).map((o,i)=>`${i+1}: ${o.label}`).join('. ')
      await speak(`${question.message} ${options}. Say the number.`,'choice')
    } else {
      void client.invalidateQueries()
      const answer=latest.status==='failed'?(latest.error??'Sorry, that did not work.'):(latest.reply??'Done.')
      setReply(answer);await speak(answer,'listening')
    }
  },[client,speak])

  const submit=useCallback(async(text:string)=>{
    if(commandTimer.current)clearTimeout(commandTimer.current)
    if(SLEEP.test(text)){await speak('Okay, agent mode is off.','listening');stopListening();setPhase('off');return}
    stopListening();setPhase('thinking');setCommand(text);setReply('');setError('');setRun(null)
    try{
      const created=await api<Run>('/commands','POST',{input:text,conversation_id:conversation.current})
      conversation.current=created.conversation_id;setRun(created);runRef.current=created
      void client.invalidateQueries({queryKey:['runs']})
      await settle(created)
    }catch(e){await speak((e as Error).message,'listening')}
  },[client,settle,setPhase,speak,stopListening])

  const answer=useCallback(async(body:{approved?:boolean;selected_id?:string})=>{
    const current=runRef.current;if(!current)return
    stopListening();setPhase('thinking')
    try{await api(`/agent-runs/${current.id}/resume`,'POST',body);await settle({...current,status:'executing',question:null})}
    catch(e){await speak((e as Error).message,'listening')}
  },[settle,setPhase,speak,stopListening])

  const cancel=useCallback(async()=>{
    const current=runRef.current;if(!current)return
    try{await api(`/agent-runs/${current.id}/cancel`,'POST');void client.invalidateQueries({queryKey:['runs']});await speak('Okay, cancelled.','listening')}
    catch(e){await speak((e as Error).message,'listening')}
  },[client,speak])

  handleRef.current=(text,direct)=>{
    const current=phaseRef.current
    if(current==='listening'){
      // Push-to-talk speech is addressed to the agent, so it needs no wake word.
      const said=direct?(parseWake(text)??text):parseWake(text);if(said===null)return
      if(said){void submit(said);return}
      void speak('Yes?','command').then(()=>{commandTimer.current=setTimeout(()=>{if(phaseRef.current==='command')setPhase('listening')},9000)})
    } else if(current==='command'){void submit(parseWake(text)||text)}
    else if(current==='permission'){
      const decision=parseDecision(text)
      if(decision===null)void speak('Please say yes or no.','permission');else void answer({approved:decision})
    } else if(current==='choice'){
      const options=runRef.current?.question?.options??[],index=parseChoice(text,options.length)
      if(index===null&&NO.test(text))void cancel()
      else if(index===null)void speak(`Say a number from 1 to ${options.length}.`,'choice');else void answer({selected_id:options[index].id})
    }
  }

  const enable=useCallback(()=>{
    setError('');setPhase('listening');startListening()
    // Browsers only allow speech output after a user gesture; this click unlocks it.
    void speak('Agent mode is on. Say hey agent, then your request.','listening')
  },[setPhase,speak,startListening])
  const disable=useCallback(()=>{
    if(commandTimer.current)clearTimeout(commandTimer.current)
    window.speechSynthesis?.cancel();ear.current?.destroy();ear.current=null;setEngine(null);setHearing(false);setPhase('off');setHeard('')
  },[setPhase])
  disableRef.current=disable
  const silence=useCallback(()=>{window.speechSynthesis?.cancel()},[])

  /** Push-to-talk: interrupts speech and records until released, no wake word needed. */
  const talkStart=useCallback(()=>{
    const current=phaseRef.current
    if(current==='off'||current==='thinking'||!ear.current)return
    if(current==='speaking'){speech.current++;window.speechSynthesis?.cancel();setPhase(runRef.current?.status==='awaiting_input'?(runRef.current.question?.kind==='selection'?'choice':'permission'):'listening');ear.current.resume()}
    setTalking(true);ear.current.pushStart()
  },[setPhase])
  const talkEnd=useCallback(()=>{setTalking(false);ear.current?.pushEnd()},[])
  const on=phase!=='off'
  useEffect(()=>{
    if(!on)return
    const typing=(e:KeyboardEvent)=>{const t=e.target as HTMLElement|null;return !!t&&(t.isContentEditable||['INPUT','TEXTAREA','SELECT'].includes(t.tagName))}
    const down=(e:KeyboardEvent)=>{if(e.code==='Space'&&!e.repeat&&!typing(e)){e.preventDefault();talkStart()}}
    const up=(e:KeyboardEvent)=>{if(e.code==='Space'&&!typing(e)){e.preventDefault();talkEnd()}}
    window.addEventListener('keydown',down);window.addEventListener('keyup',up)
    return()=>{window.removeEventListener('keydown',down);window.removeEventListener('keyup',up)}
  },[on,talkStart,talkEnd])
  useEffect(()=>()=>{window.speechSynthesis?.cancel();ear.current?.destroy()},[])
  return {phase,heard,command,run,reply,error,hearing,talking,engine,enabled:phase!=='off',enable,disable,silence,answer,submit,talkStart,talkEnd}
}
export type AgentMode=ReturnType<typeof useAgentMode>

const PHASE_TEXT:Record<Phase,string>={off:'Off',listening:'Listening for “Hey Agent”',command:'Listening… say your request',
  thinking:'Working on it…',permission:'Waiting for your permission',choice:'Waiting for your choice',speaking:'Speaking'}

export function AgentModeToggle({agent}:{agent:AgentMode}) {
  return <button className={'agent-toggle '+(agent.enabled?'on':'')} onClick={agent.enabled?agent.disable:agent.enable} aria-pressed={agent.enabled}
    title={agent.enabled?'Turn agent mode off':'Turn agent mode on: always listening for “Hey Agent”'}>
    {agent.enabled?<Mic size={16}/>:<MicOff size={16}/>}<span>Agent mode</span><i/></button>
}

export function AgentModeDock({agent}:{agent:AgentMode}) {
  if(!agent.enabled&&!agent.error)return null
  const question=agent.run?.status==='awaiting_input'?agent.run.question:null
  const waiting=agent.phase==='permission'||agent.phase==='choice'||(agent.phase==='speaking'&&!!question)
  return <aside className={'agent-dock phase-'+agent.phase} aria-live="polite" aria-label="Agent mode">
    <header><span className="agent-orb">{agent.phase==='thinking'?<Loader2 size={17} className="spin"/>:agent.phase==='speaking'?<Volume2 size={17}/>:<Bot size={17}/>}</span>
      <div><strong>AgentOS</strong><span>{PHASE_TEXT[agent.phase]}</span></div>
      {agent.phase==='speaking'&&<button className="icon-button" aria-label="Stop speaking" onClick={agent.silence}><Square size={14}/></button>}
      <button className="icon-button" aria-label="Turn agent mode off" onClick={agent.disable}><X size={16}/></button></header>
    {agent.error&&<p className="agent-error">{agent.error}</p>}
    {agent.enabled&&agent.phase==='listening'&&!agent.command&&<p className="agent-hint">Try: “Hey Agent, check my latest 3 emails and summarize them.”</p>}
    {agent.command&&<p className="agent-command"><span>You</span>{agent.command}</p>}
    {question&&waiting&&<div className="agent-question"><ShieldCheck size={17}/><div><strong>{question.message}</strong>
      {question.kind==='approval'?<div className="agent-actions"><button className="btn primary" onClick={()=>void agent.answer({approved:true})}><Check size={14}/>Allow</button><button className="btn secondary" onClick={()=>void agent.answer({approved:false})}>Deny</button></div>
      :<div className="agent-options">{question.options?.map((o,i)=><button key={o.id} onClick={()=>void agent.answer({selected_id:o.id})}><b>{i+1}</b>{o.label}</button>)}</div>}
      <small>Or just say {question.kind==='approval'?'“yes” or “no”':'the number'}.</small></div></div>}
    {agent.reply&&!question&&<p className="agent-reply">{agent.reply}</p>}
    {/Gmail is not connected/.test(agent.reply)&&!question&&<GmailConnect/>}
    {agent.enabled&&agent.heard&&<p className="agent-heard"><Mic size={12}/>{agent.heard}</p>}
    {agent.enabled&&<footer className="agent-talk">
      <button className={'agent-talk-button '+(agent.talking?'active':'')} disabled={agent.phase==='thinking'||!agent.engine}
        onPointerDown={e=>{e.preventDefault();agent.talkStart()}} onPointerUp={agent.talkEnd} onPointerLeave={()=>{if(agent.talking)agent.talkEnd()}}>
        <Mic size={15}/>{agent.talking?'Listening… release to send':'Hold to talk'}</button>
      <span>{agent.talking?'':agent.hearing?'Hearing speech…':'or hold Space'}<small>{agent.engine==='whisper'?'Whisper · noise filtered':agent.engine==='browser'?'Browser recognition':'Starting microphone…'}</small></span>
    </footer>}
  </aside>
}
