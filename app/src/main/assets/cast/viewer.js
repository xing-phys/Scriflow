'use strict';
(() => {
  const byId = id => document.getElementById(id);
  const login = byId('login'), screen = byId('screen'), viewer = byId('viewer'), status = byId('status');
  const password = byId('password'), connect = byId('connect'), logout = byId('logout'), full = byId('fullscreen');
  let connected = false, generation = 0, timer, controller, imageUrl, etag;
  function clearImage() {
    screen.hidden = true; screen.removeAttribute('src'); etag = undefined;
    if (imageUrl) URL.revokeObjectURL(imageUrl);
    imageUrl = undefined;
  }
  function cancel() { generation++; clearTimeout(timer); if (controller) controller.abort(); clearImage(); }
  function showLogin(message) {
    connected = false; cancel(); login.hidden = false; viewer.hidden = true; logout.hidden = true; full.hidden = true;
    status.textContent = message;
  }
  async function request(path, options = {}) {
    controller = new AbortController(); const active = controller;
    const timeout = setTimeout(() => active.abort(), 6000);
    try {
      const response = await fetch(path, {...options, cache: 'no-store', credentials: 'same-origin', signal: active.signal});
      const body = path === '/frame' && response.status === 200 ? await response.blob() : await response.text();
      return {status: response.status, ok: response.ok, headers: response.headers, body};
    }
    finally { clearTimeout(timeout); }
  }
  async function poll() {
    if (!connected || document.hidden) return;
    const own = generation;
    try {
      const response = await request('/frame', {headers: etag ? {'If-None-Match': etag} : {}});
      if (own !== generation) return;
      if (response.status === 401) { showLogin('Enter the password to reconnect.'); return; }
      if (response.status === 204) {
        clearImage(); status.textContent = response.headers.get('X-Cast-State') === 'paused'
          ? 'Cast paused. Return to Scriflow on the tablet. Settings and cast controls stay private.'
          : 'Waiting for the tablet screen…';
      } else if (response.status === 200) {
        const blob = response.body; if (own !== generation) return;
        if (blob.type !== 'image/jpeg') throw new Error('Invalid frame');
        const previous = imageUrl; imageUrl = URL.createObjectURL(blob);
        screen.src = imageUrl; screen.hidden = false; etag = response.headers.get('ETag');
        if (previous) URL.revokeObjectURL(previous);
        status.textContent = 'Live · View only';
      } else if (response.status !== 304) throw new Error('Disconnected');
    } catch (error) {
      if (own !== generation) return;
      clearImage(); status.textContent = 'Connection lost. Check that casting is on and both devices use the same Wi-Fi.';
    }
    if (own === generation) timer = setTimeout(poll, 300);
  }
  function showViewer() {
    connected = true; cancel(); login.hidden = true; viewer.hidden = false; logout.hidden = false;
    full.hidden = !(viewer.requestFullscreen || viewer.webkitRequestFullscreen);
    password.value = ''; status.textContent = 'Connecting to the tablet…'; poll();
  }
  login.addEventListener('submit', async event => {
    event.preventDefault(); cancel(); const own = generation; connect.disabled = true;
    const value = password.value; password.value = '';
    try {
      const response = await request('/login', {method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'}, body: new URLSearchParams({password: value})});
      if (own !== generation) return;
      if (response.ok) showViewer();
      else { status.textContent = response.body; password.focus(); }
    } catch (error) { if (own === generation) status.textContent = 'Cannot reach the tablet. Check Wi-Fi and the address.'; }
    finally { connect.disabled = false; }
  });
  logout.addEventListener('click', async () => {
    showLogin('Disconnected.'); connect.disabled = true;
    try { await request('/logout', {method: 'POST'}); }
    catch (error) { status.textContent = 'Disconnected here. Stop casting on the tablet to end all sessions.'; }
    finally { connect.disabled = false; }
  });
  full.addEventListener('click', () => {
    const enter = viewer.requestFullscreen || viewer.webkitRequestFullscreen;
    if (enter) Promise.resolve(enter.call(viewer)).catch(() => { status.textContent = 'Full screen is unavailable in this browser.'; });
  });
  document.addEventListener('visibilitychange', () => { cancel(); if (!document.hidden && connected) poll(); });
  window.addEventListener('pagehide', cancel);
  const initial = generation;
  request('/session').then(response => { if (initial === generation && response.ok) showViewer(); }).catch(() => {});
})();
