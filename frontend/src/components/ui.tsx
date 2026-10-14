import type { ReactNode } from 'react'
import * as Dialog from '@radix-ui/react-dialog'
import { X, Inbox, Loader2 } from 'lucide-react'
import { label } from '../api'

export function Badge({value}:{value:string}) { return <span className={'badge '+value}>{label(value)}</span> }
export function Empty({title,description,action}:{title:string;description:string;action?:ReactNode}) { return <div className="empty"><div className="empty-icon"><Inbox size={26}/></div><h3>{title}</h3><p>{description}</p>{action}</div> }
export function Loading() { return <div className="loading"><Loader2 className="spin" size={22}/> Loading your workspace…</div> }
export function ErrorBox({message}:{message?:string}) { return message ? <div className="error" role="alert">{message}</div> : null }
export function Modal({open,onOpenChange,title,description,children}:{open:boolean;onOpenChange:(v:boolean)=>void;title:string;description?:string;children:ReactNode}) {
  return <Dialog.Root open={open} onOpenChange={onOpenChange}><Dialog.Portal><Dialog.Overlay className="modal-overlay"/><Dialog.Content className="modal"><div className="modal-heading"><Dialog.Title>{title}</Dialog.Title><Dialog.Close className="icon-button" aria-label="Close dialog"><X size={20}/></Dialog.Close></div><Dialog.Description className="muted">{description || 'Enter the details below.'}</Dialog.Description>{children}</Dialog.Content></Dialog.Portal></Dialog.Root>
}
export function ResultTable({rows}:{rows:Record<string,unknown>[]}) {
  if (!rows.length) return <p className="muted">No matching records.</p>
  const columns=Object.keys(rows[0]).filter(k=>!['password_hash','user_id'].includes(k))
  return <div className="table-wrap"><table><thead><tr>{columns.map(k=><th key={k}>{label(k)}</th>)}</tr></thead><tbody>{rows.map((row,i)=><tr key={i}>{columns.map(k=><td key={k}>{row[k]===null?'—':typeof row[k]==='object'?JSON.stringify(row[k]):String(row[k])}</td>)}</tr>)}</tbody></table></div>
}
