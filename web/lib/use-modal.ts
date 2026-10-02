"use client";
import {useEffect} from 'react';
/** Keep keyboard focus inside an open dialog and restore it when dismissed. */
export function useModal(active:boolean,onClose:()=>void) {
 useEffect(()=>{
  if(!active)return;
  const previous=document.activeElement as HTMLElement|null;
  const modal=document.querySelector<HTMLElement>('[role="dialog"]');
  if(!modal)return;
  const selector='button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex="0"]';
  modal.querySelector<HTMLElement>(selector)?.focus();
  const handle=(e:KeyboardEvent)=>{
   if(e.key==='Escape'){e.preventDefault();onClose();}
   if(e.key==='Tab'){
    const nodes=[...modal.querySelectorAll<HTMLElement>(selector)].filter(el=>el.getClientRects().length);
    const first=nodes[0],last=nodes.at(-1);
    if(e.shiftKey&&document.activeElement===first){e.preventDefault();last?.focus();}
    else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first?.focus();}
   }
  };
  document.addEventListener('keydown',handle);
  return()=>{document.removeEventListener('keydown',handle);previous?.focus();};
 },[active,onClose]);
}
