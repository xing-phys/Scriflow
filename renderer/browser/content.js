import { captureDocument, pageStats } from './capture.js';
const port = browser.runtime.connectNative('scriflow');
let active = null;
const acknowledgements = new Map();
port.onMessage.addListener(async message => {
  if (message.type === 'ack') { acknowledgements.get(message.sequence)?.(); acknowledgements.delete(message.sequence); return; }
  if (message.type === 'cancel') {
    const id = active; active = null;
    if (id) browser.runtime.sendMessage({ type: 'cancel', id }).catch(() => {});
    for (const resolve of acknowledgements.values()) resolve(); acknowledgements.clear(); return;
  }
  if (message.type === 'stats') { port.postMessage({ type: 'stats', id: message.id, ...pageStats(document) }); return; }
  if (message.type !== 'capture' || active) return;
  const id = active = message.id;
  try {
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
      port.postMessage({ type: 'chunk', id, sequence: current, text }); await ack; offset = end;
    }
    port.postMessage({ type: 'complete', id, chunks: sequence, url: result.url, missing: result.missing });
  } catch (error) { if (active === id) port.postMessage({ type: 'error', id, message: String(error.message) }); }
  finally { if (active === id) { active = null; browser.runtime.sendMessage({ type: 'end', id }).catch(() => {}); } }
});
