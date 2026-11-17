import { captureDocument, pageStats } from './capture.js';
let port = null, active = null, hidden = false, retry = null, heartbeat = null, announcements = 0;
let retryDelay = 250;
const visible = () => !hidden && document.visibilityState !== 'hidden';
const acknowledgements = new Map();
const announce = () => {
  clearTimeout(heartbeat); heartbeat = null;
  if (!visible() || !port) return;
  try { port?.postMessage({ type: 'document', url: location.href, readable: document.readyState !== 'loading' && !!document.body }); } catch { }
  // A few early announcements bridge native-event ordering without polling a
  // permanently loading page for the rest of the reading session.
  if (document.readyState !== 'complete' && announcements++ < 20) heartbeat = setTimeout(announce, 500);
};
function retire() {
  const id = active; active = null;
  if (id) browser.runtime.sendMessage({ type: 'cancel', id }).catch(() => {});
  for (const resolve of acknowledgements.values()) resolve(); acknowledgements.clear();
}
function reconnectLater() {
  clearTimeout(retry); retry = null;
  if (!visible()) return;
  retry = setTimeout(connect, retryDelay);
  retryDelay = Math.min(30000, retryDelay * 2);
}
function connect() {
  clearTimeout(retry); retry = null;
  if (!visible() || port) return;
  announcements = 0;
  try {
    const owner = port = browser.runtime.connectNative('scriflow');
    owner.onMessage.addListener(message => receive(owner, message));
    owner.onDisconnect.addListener(() => {
      if (port !== owner) return;
      port = null; retire();
      clearTimeout(heartbeat); heartbeat = null;
      reconnectLater();
    });
    announce();
  } catch { port = null; reconnectLater(); }
}
document.addEventListener('DOMContentLoaded', announce);
addEventListener('load', announce);
addEventListener('hashchange', announce);
addEventListener('popstate', announce);
addEventListener('pagehide', () => { hidden = true; clearTimeout(retry); clearTimeout(heartbeat); retire(); const old = port; port = null; old?.disconnect(); });
addEventListener('pageshow', () => { hidden = false; connect(); announce(); });
document.addEventListener('visibilitychange', () => {
  if (visible()) { connect(); announce(); }
  else { clearTimeout(retry); retry = null; clearTimeout(heartbeat); heartbeat = null; }
});
connect();

async function receive(owner, message) {
  if (owner !== port) return;
  if (message.type === 'ack') { if (message.id === active) { acknowledgements.get(message.sequence)?.(); acknowledgements.delete(message.sequence); } return; }
  if (message.type === 'cancel') { if (message.id === active) retire(); return; }
  if (message.type === 'hello') { retryDelay = 250; announce(); return; }
  if (message.type === 'authorize-popup') {
    try { await browser.runtime.sendMessage({type:'authorize-popup',url:message.url});owner.postMessage({type:'popup-authorized',id:message.id,ok:true}); }
    catch { owner.postMessage({type:'popup-authorized',id:message.id,ok:false}); }
    return;
  }
  if (message.type === 'stats') { owner.postMessage({ type: 'stats', id: message.id, ...pageStats(document) }); return; }
  if (message.type !== 'capture' || active) return;
  const id = active = message.id;
  try {
    if (!document.documentElement || !document.body) throw Error('The document has no readable content yet');
    await browser.runtime.sendMessage({ type: 'begin', id });
    // Observe the rendered document without invoking JavaScript supplied by a site.
    // MathJax may still be replacing formula nodes when the load event arrives.
    let last = '', stable = 0;
    for (let i = 0; i < 40 && active === id; i++) {
      const state = [document.querySelectorAll('.math').length, document.querySelectorAll('mjx-container, .katex').length,
        document.querySelectorAll('mjx-assistive-mml').length, document.fonts?.status].join(':');
      stable = state === last ? stable + 1 : 0; last = state;
      if (stable >= 4 && document.fonts?.status !== 'loading') break;
      await new Promise(resolve => setTimeout(resolve, 250));
    }
    const result = await captureDocument(document, async url => {
      if (active !== id) throw Error('Retired capture');
      return browser.runtime.sendMessage({ type: 'fetch', id, url });
    });
    if (active !== id) return;
    let sequence = 0;
    for (let offset = 0; offset < result.html.length;) {
      if (active !== id) return;
      let end = Math.min(result.html.length, offset + 65536);
      const last = result.html.charCodeAt(end - 1); if (end < result.html.length && last >= 0xd800 && last <= 0xdbff) end--;
      const text = result.html.slice(offset, end), current = sequence++;
      const ack = new Promise((resolve, reject) => {
        const timeout = setTimeout(() => { acknowledgements.delete(current); reject(Error('Capture writer timed out')); }, 15000);
        acknowledgements.set(current, () => { clearTimeout(timeout); resolve(); });
      });
      owner.postMessage({ type: 'chunk', id, sequence: current, text }); await ack; offset = end;
    }
    owner.postMessage({ type: 'complete', id, chunks: sequence, url: result.url, missing: result.missing });
  } catch (error) { if (active === id && owner === port) owner.postMessage({ type: 'error', id,
    code: /size limit|exceeds|too large/i.test(String(error.message)) ? 'size' : 'capture' }); }
  finally { if (active === id) { active = null; browser.runtime.sendMessage({ type: 'end', id }).catch(() => {}); } }
}
