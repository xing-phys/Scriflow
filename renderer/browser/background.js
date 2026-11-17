import './data.js';
import { allowedRequest } from './policy.js';
const modes = new Map(), captures = new Map(), popupGrants = [];
const ownOrigin = browser.runtime.getURL('');
browser.webRequest.onBeforeRequest.addListener(details => {
  // Extension-owned fetches are individually validated below. User tabs must be
  // configured before navigation; an offline tab never falls back to the network.
  let mode = details.tabId < 0 ? { offline: false } : modes.get(details.tabId);
  if(!mode && details.type==='main_frame' && details.tabId>=0) {
    // GeckoView does not expose openerTabId reliably. A trusted native request
    // authorizes one exact initial URL, never an unknown tab or subresource.
    const now=Date.now();for(let i=popupGrants.length-1;i>=0;i--)if(popupGrants[i].expires<now||!modes.has(popupGrants[i].owner))popupGrants.splice(i,1);
    const index=popupGrants.findIndex(grant=>grant.url===details.url);
    if(index>=0){popupGrants.splice(index,1);mode={offline:false};modes.set(details.tabId,mode);}
  }
  return { cancel: !allowedRequest(details.url, mode, ownOrigin) };
}, { urls: ['<all_urls>'] }, ['blocking']);
browser.tabs.onRemoved.addListener(id => { modes.delete(id); captures.delete(id);for(let i=popupGrants.length-1;i>=0;i--)if(popupGrants[i].owner===id)popupGrants.splice(i,1); });
browser.runtime.onMessage.addListener(async (message, sender) => {
  const tab = sender.tab?.id;
  if (tab == null || sender.frameId && sender.frameId !== 0) throw Error('Invalid capture owner');
  if (message.type === 'configure' && sender.url.startsWith(ownOrigin)) {
    const archive = String(message.archive || '').split('#')[0];
    if (message.offline && !archive.startsWith('file:///')) throw Error('Invalid offline archive');
    modes.set(tab, { offline: !!message.offline, archive }); return { ready: true };
  }
  if (!modes.has(tab) || modes.get(tab).offline) throw Error('Capture is only available online');
  if(message.type==='authorize-popup') {
    const url=new URL(message.url);if(url.protocol!=='https:')throw Error('Invalid popup URL');url.hash='';
    const now=Date.now();for(let i=popupGrants.length-1;i>=0;i--)if(popupGrants[i].expires<now||!modes.has(popupGrants[i].owner))popupGrants.splice(i,1);
    if(popupGrants.length>=32)throw Error('Too many pending windows');
    popupGrants.push({owner:tab,url:url.href,expires:now+10000});return true;
  }
  if (message.type === 'begin') {
    if (!/^[a-f0-9-]{36}$/.test(message.id)) throw Error('Invalid capture');
    const old = captures.get(tab);
    if (old) { for (const controller of old.controllers) controller.abort(); for (const resume of old.queue.splice(0)) resume(); }
    captures.set(tab, { id: message.id, bytes: 0, active: 0, queue: [], cache: new Map(), controllers: new Set() });
    return true;
  }
  const capture = captures.get(tab);
  if (!capture || capture.id !== message.id) throw Error('Retired capture');
  if (message.type === 'end' || message.type === 'cancel') {
    captures.delete(tab); for (const controller of capture.controllers) controller.abort();
    for (const resume of capture.queue.splice(0)) resume(); return true;
  }
  if (message.type !== 'fetch') throw Error('Unknown capture command');
  const url = new URL(message.url); url.hash = '';
  if (url.protocol !== 'https:') throw Error('Only HTTPS resources can be captured');
  if (capture.cache.has(url.href)) return capture.cache.get(url.href);
  const request = (async () => {
    if (capture.active >= 6) await new Promise(resolve => capture.queue.push(resolve));
    if (captures.get(tab) !== capture) throw Error('Retired capture');
    capture.active++;
    const controller = new AbortController(); capture.controllers.add(controller);
    const timeout = setTimeout(() => controller.abort(), 12000);
    try {
      const response = await fetch(url.href, { credentials: 'include', cache: 'force-cache', signal: controller.signal });
      if (!response.ok) throw Error('Resource returned HTTP ' + response.status);
      const reader = response.body.getReader(), chunks = []; let bytes = 0;
      for (;;) {
        const { value, done } = await reader.read(); if (done) break;
        bytes += value.byteLength; capture.bytes += value.byteLength;
        if (bytes > 8 * 1024 * 1024 || capture.bytes > 64 * 1024 * 1024) { await reader.cancel(); throw Error('Page exceeds capture size limit'); }
        chunks.push(value);
      }
      const data = new Uint8Array(bytes); let offset = 0;
      for (const chunk of chunks) { data.set(chunk, offset); offset += chunk.length; }
      let binary = '';
      for (let i = 0; i < bytes; i += 32768) binary += String.fromCharCode(...data.subarray(i, i + 32768));
      return { data: btoa(binary), mime: response.headers.get('content-type')?.split(';')[0] || 'application/octet-stream', url: response.url };
    } finally {
      clearTimeout(timeout); capture.controllers.delete(controller); capture.active--;
      capture.queue.shift()?.();
    }
  })();
  capture.cache.set(url.href, request); return request;
});
