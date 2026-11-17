'use strict';
(() => {
  const byId = id => document.getElementById(id);
  const login = byId('login'), viewer = byId('viewer'), status = byId('status'), notice = byId('notice');
  const password = byId('password'), connect = byId('connect'), logout = byId('logout'), full = byId('fullscreen');
  let screen = byId('screen'), spare = byId('screen-next');
  let connected = false, generation = 0, timer, controller, imageUrl, pendingUrl, etag;
  let blackSince = -1;
  const sample = document.createElement('canvas'); sample.width = 32; sample.height = 24;
  const pixels = sample.getContext('2d', {willReadFrequently: true});
  function blackFrame(image) {
    pixels.drawImage(image, 0, 0, 32, 24);
    const data = pixels.getImageData(0, 0, 32, 24).data;
    for (let i = 0; i < data.length; i += 4) if (data[i] > 2 || data[i + 1] > 2 || data[i + 2] > 2) return false;
    return true;
  }
  function message(text) { status.textContent = text; notice.textContent = text; notice.hidden = !screen.hidden; }
  function clearImage() {
    for (const image of [screen, spare]) { image.hidden = true; image.removeAttribute('src'); }
    etag = undefined;
    if (imageUrl) URL.revokeObjectURL(imageUrl);
    if (pendingUrl) URL.revokeObjectURL(pendingUrl);
    imageUrl = pendingUrl = undefined; notice.hidden = false;
    blackSince = -1;
  }
  function cancel() { generation++; clearTimeout(timer); if (controller) controller.abort(); clearImage(); }
  function showLogin(text) {
    connected = false; cancel(); login.hidden = false; viewer.hidden = true; logout.hidden = true; full.hidden = true;
    message(text);
  }
  async function request(path, options = {}) {
    controller = new AbortController(); const active = controller;
    const timeout = setTimeout(() => active.abort(), 6000);
    try {
      const response = await fetch(path, {...options, cache: 'no-store', credentials: 'same-origin', signal: active.signal});
      const body = path === '/frame' && response.status === 200 ? await response.blob() : await response.text();
      return {status: response.status, ok: response.ok, headers: response.headers, body};
    } finally { clearTimeout(timeout); }
  }
  async function display(blob, own) {
    const target = spare, url = URL.createObjectURL(blob); pendingUrl = url;
    try {
      target.src = url;
      if (target.decode) await target.decode();
      else await new Promise((resolve, reject) => { target.onload = resolve; target.onerror = reject; if (target.complete) target.naturalWidth ? resolve() : reject(); });
      if (!target.naturalWidth) throw new Error('Undecodable frame');
      if (own !== generation || document.hidden) return false;
      if (blackFrame(target)) {
        if (blackSince < 0) blackSince = performance.now();
        // Do not turn a brief compositor/secure-window transition into a black lecture slide.
        // Persistent black content is displayed normally after this short confirmation window.
        if (performance.now() - blackSince < 250) return false;
      } else blackSince = -1;
      await new Promise(resolve => requestAnimationFrame(resolve));
      if (own !== generation || document.hidden) return false;
      const old = screen, previous = imageUrl;
      target.id = 'screen'; old.id = 'screen-next'; target.hidden = false; old.hidden = true;
      screen = target; spare = old; imageUrl = url; pendingUrl = undefined;
      spare.removeAttribute('src');
      if (previous) URL.revokeObjectURL(previous);
      return true;
    } finally {
      if (imageUrl !== url) { URL.revokeObjectURL(url); if (pendingUrl === url) pendingUrl = undefined; }
    }
  }
  async function poll() {
    if (!connected || document.hidden) return;
    const own = generation; let delay = 0;
    try {
      const response = await request('/frame', {headers: {...(etag ? {'If-None-Match': etag} : {}), Prefer: 'wait=1'}});
      if (own !== generation) return;
      if (response.status === 401) { showLogin('Enter the password to reconnect.'); return; }
      if (response.status === 204) {
        const state = response.headers.get('X-Cast-State');
        if (state === 'paused') { clearImage(); message('Cast paused. Return to Scriflow on the tablet. Settings and cast controls stay private.'); delay = 200; }
        else { if (screen.hidden) message('Waiting for the tablet screen…'); delay = 50; }
      } else if (response.status === 200) {
        if (response.body.type !== 'image/jpeg') throw new Error('Invalid frame');
        try {
          if (await display(response.body, own)) { etag = response.headers.get('ETag'); message('Live · View only'); }
          else delay = 40;
        } catch (decodeError) {
          if (own === generation) { message('Recovering the next frame…'); delay = 60; }
        }
      } else if (response.status !== 304) throw new Error('Disconnected');
    } catch (error) {
      if (own !== generation) return;
      clearImage(); message('Connection lost. Check that casting is on and both devices use the same Wi-Fi.'); delay = 500;
    }
    if (own === generation) timer = setTimeout(poll, delay);
  }
  function showViewer() {
    connected = true; cancel(); login.hidden = true; viewer.hidden = false; logout.hidden = false;
    full.hidden = !(viewer.requestFullscreen || viewer.webkitRequestFullscreen);
    password.value = ''; message('Connecting to the tablet…'); poll();
  }
  login.addEventListener('submit', async event => {
    event.preventDefault(); cancel(); const own = generation; connect.disabled = true;
    const value = password.value; password.value = '';
    try {
      const response = await request('/login', {method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'}, body: new URLSearchParams({password: value})});
      if (own !== generation) return;
      if (response.ok) showViewer(); else { message(response.body); password.focus(); }
    } catch (error) { if (own === generation) message('Cannot reach the tablet. Check Wi-Fi and the address.'); }
    finally { connect.disabled = false; }
  });
  logout.addEventListener('click', async () => {
    showLogin('Disconnected.'); connect.disabled = true;
    try { await request('/logout', {method: 'POST'}); }
    catch (error) { message('Disconnected here. Stop casting on the tablet to end all sessions.'); }
    finally { connect.disabled = false; }
  });
  full.addEventListener('click', () => {
    const enter = viewer.requestFullscreen || viewer.webkitRequestFullscreen;
    if (enter) Promise.resolve(enter.call(viewer)).catch(() => message('Full screen is unavailable in this browser.'));
  });
  document.addEventListener('visibilitychange', () => { cancel(); if (!document.hidden && connected) poll(); });
  window.addEventListener('pagehide', cancel);
  const initial = generation;
  request('/session').then(response => { if (initial === generation && response.ok) showViewer(); }).catch(() => {});
})();
