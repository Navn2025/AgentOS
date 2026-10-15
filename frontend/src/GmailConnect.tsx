import { useState } from 'react'
import type { FormEvent } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Mail } from 'lucide-react'
import { api } from './api'
import { ErrorBox, Modal } from './components/ui'

/** Saves a Gmail App Password after Google accepts it, so the agent can read the inbox (read-only). */
export function GmailConnect({onConnected}:{onConnected?:()=>void}) {
  const [open,setOpen]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState(''),client=useQueryClient()
  async function submit(e:FormEvent<HTMLFormElement>) {
    e.preventDefault();setBusy(true);setError('')
    const form=new FormData(e.currentTarget)
    try{await api('/laptop/gmail','POST',{address:form.get('address'),app_password:form.get('app_password')});setOpen(false);void client.invalidateQueries({queryKey:['config']});onConnected?.()}
    catch(err){setError((err as Error).message)}finally{setBusy(false)}
  }
  return <><button className="btn secondary" onClick={()=>setOpen(true)}><Mail size={14}/>Connect Gmail</button>
    <Modal open={open} onOpenChange={setOpen} title="Connect Gmail" description="AgentOS reads your inbox read-only (messages stay unread) and only after you allow each request.">
      <ol className="gmail-steps">
        <li>Turn on 2-Step Verification for your Google account.</li>
        <li>Open <a href="https://myaccount.google.com/apppasswords" target="_blank" rel="noreferrer">myaccount.google.com/apppasswords</a>, create a password named “AgentOS”, and copy the 16 letters.</li>
      </ol>
      <form onSubmit={submit}>
        <label>Gmail address<input name="address" type="email" autoComplete="email" required placeholder="you@gmail.com"/></label>
        <label>App Password<input name="app_password" type="password" autoComplete="off" required minLength={16} placeholder="abcd efgh ijkl mnop"/></label>
        <ErrorBox message={error}/>
        <button className="btn primary full" disabled={busy}>{busy?'Checking with Google…':'Connect'}</button>
      </form>
    </Modal></>
}
