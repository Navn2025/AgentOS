import { transcribe } from './api'

/** A microphone pipeline for agent mode. `direct` text came from push-to-talk and needs no wake word. */
export interface Ear {
  kind:'whisper'|'browser'
  pause:()=>void
  resume:()=>void
  pushStart:()=>void
  pushEnd:()=>void
  destroy:()=>void
}
export type EarEvents={onText:(text:string,direct:boolean)=>void;onHearing:(hearing:boolean)=>void;onError:(message:string)=>void}

const constraints:MediaTrackConstraints={channelCount:1,noiseSuppression:true,echoCancellation:true,autoGainControl:true}

/**
 * Silero VAD (local, neural) cuts real speech out of background noise; only those clips go to Whisper.
 * Noise suppression and echo cancellation come from the browser's audio processing.
 */
export async function createWhisperEar(events:EarEvents):Promise<Ear> {
  const stream=await navigator.mediaDevices.getUserMedia({audio:constraints})
  const {MicVAD,utils}=await import('@ricky0123/vad-web')
  let paused=false,pushing=false,recorder:MediaRecorder|null=null,chunks:Blob[]=[]
  async function send(audio:Blob,direct:boolean) {
    events.onHearing(false)
    try{const {text}=await transcribe(audio);if(text)events.onText(text,direct)}
    catch(e){events.onError((e as Error).message)}
  }
  const vad=await MicVAD.new({model:'v5',baseAssetPath:'/vad/',onnxWASMBasePath:'/vad/',
    // Stricter than the defaults so background chatter and clicks are not treated as speech.
    positiveSpeechThreshold:0.65,negativeSpeechThreshold:0.45,minSpeechMs:350,preSpeechPadMs:400,redemptionMs:800,
    getStream:async()=>stream,pauseStream:async()=>{},resumeStream:async()=>stream,
    onSpeechRealStart:()=>{if(!paused)events.onHearing(true)},
    onVADMisfire:()=>events.onHearing(false),
    onSpeechEnd:audio=>{if(paused||pushing)return;void send(new Blob([utils.encodeWAV(audio)],{type:'audio/wav'}),false)}})
  await vad.start()
  return {kind:'whisper',
    pause:()=>{paused=true;events.onHearing(false);void vad.pause()},
    resume:()=>{paused=false;void vad.start()},
    pushStart:()=>{
      if(pushing)return
      pushing=true;void vad.pause();chunks=[]
      recorder=new MediaRecorder(stream);recorder.ondataavailable=e=>{if(e.data.size)chunks.push(e.data)}
      recorder.onstop=()=>{const blob=new Blob(chunks,{type:recorder?.mimeType||'audio/webm'});recorder=null;if(blob.size>2000)void send(blob,true);else events.onHearing(false)}
      recorder.start();events.onHearing(true)
    },
    pushEnd:()=>{if(!pushing)return;pushing=false;recorder?.stop();if(!paused)void vad.start()},
    destroy:()=>{paused=true;recorder?.stop();void vad.destroy();stream.getTracks().forEach(t=>t.stop())}}
}

type Result={isFinal:boolean;[index:number]:{transcript:string}}
interface Listener {
  lang:string;continuous:boolean;interimResults:boolean
  onresult:((event:{resultIndex?:number;results:ArrayLike<Result>})=>void)|null
  onerror:((event:{error:string})=>void)|null
  onend:(()=>void)|null
  start:()=>void;stop:()=>void;abort:()=>void
}

/** Fallback: the browser's built-in recognition (less accurate in noise). */
export function createBrowserEar(events:EarEvents):Ear {
  const Constructor=(window.SpeechRecognition??window.webkitSpeechRecognition) as (new()=>Listener)|undefined
  if(!Constructor)throw new Error('Agent mode needs a microphone and speech support. Use Google Chrome or Microsoft Edge.')
  let active:Listener|null=null,paused=false,direct=false,stopped=false
  function start() {
    if(paused||stopped||active)return
    const recognition=new Constructor!();active=recognition
    recognition.lang='en-IN';recognition.continuous=true;recognition.interimResults=true
    recognition.onresult=event=>{
      for(const result of Array.from(event.results).slice(event.resultIndex??0)){
        const text=result[0].transcript.trim();if(!text)continue
        if(result.isFinal){events.onHearing(false);events.onText(text,direct)}else events.onHearing(true)
      }
    }
    recognition.onerror=event=>{
      if(event.error==='not-allowed'||event.error==='service-not-allowed'){stopped=true;events.onError('Microphone permission was denied. Allow the microphone for this site, then turn agent mode on again.')}
      else if(event.error==='network')events.onError('Speech recognition needs an internet connection. Retrying…')
      else if(event.error==='audio-capture')events.onError('No microphone was found.')
    }
    // Chrome ends continuous recognition every minute or so; restart it while listening.
    recognition.onend=()=>{if(active===recognition){active=null;setTimeout(start,250)}}
    try{recognition.start()}catch{active=null;setTimeout(start,1000)}
  }
  function stop(){const current=active;active=null;if(current){current.onend=null;current.onresult=null;current.onerror=null;try{current.abort()}catch{/* already stopped */}}}
  start()
  return {kind:'browser',pause:()=>{paused=true;stop()},resume:()=>{paused=false;start()},
    pushStart:()=>{direct=true;paused=false;start()},pushEnd:()=>{setTimeout(()=>{direct=false},1500)},
    destroy:()=>{stopped=true;stop()}}
}
