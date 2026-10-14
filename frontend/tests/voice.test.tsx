import { act, renderHook } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { useVoiceInput } from '../src/useVoiceInput'
import type { Recognition } from '../src/useVoiceInput'
afterEach(()=>{delete window.SpeechRecognition;delete window.webkitSpeechRecognition})
it('provides a typed-input fallback without microphone support',()=>{
 const callback=vi.fn();const {result}=renderHook(()=>useVoiceInput(callback));act(()=>result.current.toggle());expect(result.current.error).toContain('unavailable');expect(callback).not.toHaveBeenCalled()
})
it('returns a final editable transcript and never submits a command',()=>{
 let recognition:Recognition
 class MockRecognition implements Recognition{
  lang='';continuous=false;interimResults=false;onresult:Recognition['onresult']=null;onerror:Recognition['onerror']=null;onend:Recognition['onend']=null
  constructor(){recognition=this}start=vi.fn();stop=vi.fn(()=>this.onend?.());abort=vi.fn()
 }
 window.SpeechRecognition=MockRecognition
 const callback=vi.fn();const {result,unmount}=renderHook(()=>useVoiceInput(callback));act(()=>result.current.toggle());expect(result.current.listening).toBe(true)
 act(()=>recognition.onresult?.({results:[{isFinal:true,0:{transcript:'Create DBMS project'}}]}));expect(callback).toHaveBeenCalledWith('Create DBMS project')
 act(()=>result.current.toggle());expect(result.current.listening).toBe(false);unmount()
})
