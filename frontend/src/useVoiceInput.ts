import { useEffect, useRef, useState } from 'react'

export interface Recognition {
  lang:string;continuous:boolean;interimResults:boolean
  onresult:((event:{results:ArrayLike<{isFinal:boolean;[index:number]:{transcript:string}}>})=>void)|null
  onerror:((event:{error:string})=>void)|null
  onend:(()=>void)|null
  start:()=>void;stop:()=>void;abort:()=>void
}
declare global {interface Window{SpeechRecognition?:new()=>Recognition;webkitSpeechRecognition?:new()=>Recognition}}

export function useVoiceInput(onTranscript:(text:string)=>void) {
  const [listening,setListening]=useState(false),[error,setError]=useState('')
  const active=useRef<Recognition|null>(null),timer=useRef<ReturnType<typeof setTimeout>|null>(null)
  const callback=useRef(onTranscript);callback.current=onTranscript
  useEffect(()=>()=>{if(timer.current)clearTimeout(timer.current);if(active.current){active.current.onend=null;active.current.onresult=null;active.current.onerror=null;active.current.abort()}},[])
  function toggle(){
    if(listening){active.current?.stop();return}
    const Constructor=window.SpeechRecognition??window.webkitSpeechRecognition
    if(!Constructor){setError('Voice transcription is unavailable in this browser. Type your command instead.');return}
    setError('');const recognition=new Constructor();active.current=recognition
    recognition.lang='en-IN';recognition.continuous=false;recognition.interimResults=false
    recognition.onresult=event=>{const text=Array.from(event.results).filter(r=>r.isFinal).map(r=>r[0].transcript).join(' ').trim();if(text)callback.current(text)}
    recognition.onerror=event=>setError(event.error==='not-allowed'?'Microphone permission was denied. You can still type your command.':'Voice transcription failed. Check your microphone and internet connection, then try again.')
    recognition.onend=()=>{setListening(false);if(timer.current)clearTimeout(timer.current);active.current=null}
    try{recognition.start();setListening(true);timer.current=setTimeout(()=>recognition.stop(),60000)}catch{active.current=null;setError('The microphone could not start. Try again or type your command.')}
  }
  return {listening,error,toggle}
}
