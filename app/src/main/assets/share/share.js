'use strict';

const byId = (id) => document.getElementById(id);
const code = byId('code');
const pairStatus = byId('pair-status');
const fileStatus = byId('file-status');
const linkStatus = byId('link-status');
const link = byId('link');
const files = byId('files');
const choose = byId('choose');
const progress = byId('progress');
const drop = byId('drop');
const disconnect = byId('disconnect');
const connection = byId('connection');
let connected = false;
let refreshing = false;
let disconnecting = false;
let stateVersion = 0;
let maxFileBytes = 1024 ** 3;
let previousItems = '';
let batch = null;
let activeUpload = null;

function notice(element, value) {
  if (element.textContent !== value) element.textContent = value;
}

function showSession(active) {
  connected = active;
  byId('pairing').hidden = active;
  byId('sharing').hidden = !active;
  disconnect.hidden = !active;
  if (!active) {
    batch = null;
    if (activeUpload) activeUpload.abort();
    activeUpload = null;
    choose.disabled = false;
    progress.hidden = true;
    notice(fileStatus, '');
    notice(linkStatus, '');
    link.value = '';
    renderItems([]);
  }
}

function expired() {
  stateVersion++;
  showSession(false);
  notice(pairStatus, 'Pair again using the code on the tablet.');
}

async function request(path, options) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 8000);
  try {
    return await fetch(path, { ...options, signal: controller.signal });
  } finally {
    clearTimeout(timeout);
  }
}

function post(path, value) {
  return request(path, {
    method: 'POST',
    headers: { 'X-Scriflow-Share': '1', 'Content-Type': 'text/plain; charset=utf-8' },
    body: value,
  });
}

async function refresh() {
  if (refreshing || disconnecting) return;
  refreshing = true;
  const version = stateVersion;
  try {
    const response = await request('/items');
    if (version !== stateVersion) return;
    if (response.status === 401) {
      if (connected) expired();
      return;
    }
    if (!response.ok) throw new Error();
    const data = await response.json();
    if (version !== stateVersion) return;
    maxFileBytes = data.maxFileBytes;
    renderItems(data.items);
    notice(connection, 'Connected to the tablet');
    showSession(true);
  } catch {
    if (version !== stateVersion) return;
    if (connected) notice(connection, 'The tablet is unreachable. Check that sharing is still on.');
    else notice(pairStatus, 'Check the address and make sure sharing is on.');
  } finally {
    refreshing = false;
    if (version !== stateVersion && connected && !disconnecting) refresh();
  }
}

function sizeLabel(bytes) {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 ** 2) return (bytes / 1024).toLocaleString(undefined, { maximumFractionDigits: 1 }) + ' KB';
  if (bytes < 1024 ** 3) return (bytes / 1024 ** 2).toLocaleString(undefined, { maximumFractionDigits: 1 }) + ' MB';
  return (bytes / 1024 ** 3).toLocaleString(undefined, { maximumFractionDigits: 1 }) + ' GB';
}

function renderItems(items) {
  const key = JSON.stringify(items);
  if (key === previousItems) return;
  previousItems = key;
  const rows = document.createDocumentFragment();
  for (const item of items) {
    const row = document.createElement('li');
    const detail = document.createElement('div');
    detail.className = 'item-name';
    const title = document.createElement('p');
    title.textContent = item.name;
    title.dir = item.url ? 'ltr' : 'auto';
    detail.append(title);
    const action = document.createElement('a');
    action.className = 'action';
    if (item.url) {
      action.textContent = 'Open link';
      action.href = item.url;
      action.target = '_blank';
      action.rel = 'noopener noreferrer';
    } else {
      const size = document.createElement('p');
      size.className = 'item-size';
      size.textContent = sizeLabel(item.size);
      detail.append(size);
      action.textContent = 'Download';
      action.href = '/download/' + item.id;
      action.download = '';
    }
    row.append(detail, action);
    rows.append(row);
  }
  byId('incoming').replaceChildren(rows);
  byId('empty').hidden = items.length > 0;
}

byId('pair-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const connect = byId('connect');
  if (connect.disabled) return;
  connect.disabled = true;
  stateVersion++;
  notice(pairStatus, 'Connecting…');
  try {
    const response = await post('/connect', code.value.trim());
    if (!response.ok) { notice(pairStatus, await response.text()); return; }
    code.value = '';
    notice(pairStatus, '');
    notice(connection, 'Connected to the tablet');
    showSession(true);
    await refresh();
    choose.focus();
  } catch {
    notice(pairStatus, 'Check the address and make sure sharing is on.');
  } finally { connect.disabled = false; }
});

disconnect.addEventListener('click', async () => {
  if (disconnecting) return;
  disconnecting = true;
  disconnect.disabled = true;
  stateVersion++;
  if (activeUpload) activeUpload.abort();
  try {
    const response = await post('/disconnect');
    if (!response.ok && response.status !== 401) throw new Error();
    showSession(false);
    notice(pairStatus, 'Disconnected.');
    code.focus();
  } catch {
    notice(connection, 'Could not disconnect. Try again or stop sharing on the tablet.');
  } finally {
    disconnecting = false;
    disconnect.disabled = false;
  }
});

choose.addEventListener('click', () => files.click());
files.addEventListener('change', () => {
  const selected = Array.from(files.files);
  files.value = '';
  sendFiles(selected);
});

async function sendFiles(selected) {
  if (!connected || disconnecting || !selected.length) return;
  if (batch) { notice(fileStatus, 'Wait for the current files to finish.'); return; }
  const current = {};
  batch = current;
  choose.disabled = true;
  let saved = 0;
  let result = '';
  let error = '';
  for (const file of selected) {
    if (batch !== current || disconnecting) break;
    if (file.size > maxFileBytes) {
      if (!error) error = file.name + ' exceeds the ' + sizeLabel(maxFileBytes) + ' limit.';
      continue;
    }
    progress.hidden = false;
    progress.value = 0;
    progress.max = Math.max(1, file.size);
    notice(fileStatus, 'Sending ' + file.name + '…');
    try {
      result = await upload(file);
      saved++;
    } catch (failure) {
      if (!error) error = failure.message;
    }
  }
  if (batch !== current) return;
  batch = null;
  progress.hidden = true;
  choose.disabled = false;
  notice(fileStatus, selected.length === 1 ? error || result
    : saved + ' of ' + selected.length + ' files saved.' + (error ? ' ' + error : ''));
}

function upload(file) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', '/upload');
    xhr.setRequestHeader('X-Scriflow-Share', '1');
    xhr.setRequestHeader('Content-Type', 'application/octet-stream');
    xhr.setRequestHeader('X-File-Name', encodeURIComponent(file.name));
    xhr.upload.onprogress = (event) => {
      progress.value = event.loaded;
      if (event.loaded >= file.size) notice(fileStatus, 'Saving ' + file.name + ' on the tablet…');
    };
    xhr.onload = () => {
      if (xhr.status === 200) resolve(xhr.responseText);
      else {
        if (xhr.status === 401 && !disconnecting) expired();
        reject(new Error(xhr.responseText || 'Could not send ' + file.name + '.'));
      }
    };
    xhr.onerror = () => reject(new Error('Connection lost while sending ' + file.name + '. Try again.'));
    xhr.onabort = () => reject(new Error('Upload canceled.'));
    xhr.onloadend = () => { if (activeUpload === xhr) activeUpload = null; };
    activeUpload = xhr;
    xhr.send(file);
  });
}

async function sendLink() {
  const send = byId('send-link');
  if (!connected || disconnecting || send.disabled) return;
  const value = link.value.trim();
  if (!/^https?:\/\//i.test(value) || value.length > 8192) {
    notice(linkStatus, 'Enter a full HTTP or HTTPS address.');
    return;
  }
  const version = stateVersion;
  send.disabled = true;
  notice(linkStatus, 'Sending link…');
  try {
    const response = await post('/link', value);
    const message = await response.text();
    if (version !== stateVersion) return;
    if (response.status === 401) { expired(); return; }
    if (response.ok && link.value.trim() === value) link.value = '';
    notice(linkStatus, message);
  } catch {
    if (version === stateVersion) notice(linkStatus, 'Connection lost. Try sending the link again.');
  } finally { send.disabled = false; }
}

byId('link-form').addEventListener('submit', (event) => {
  event.preventDefault();
  sendLink();
});

function supportedDrop(transfer) {
  return transfer && Array.from(transfer.types).some((type) => ['Files', 'text/uri-list', 'text/plain'].includes(type));
}

document.addEventListener('dragover', (event) => {
  if (!supportedDrop(event.dataTransfer)) return;
  event.preventDefault();
  event.dataTransfer.dropEffect = connected ? 'copy' : 'none';
  drop.classList.toggle('dropping', connected && Array.from(event.dataTransfer.types).includes('Files'));
});
document.addEventListener('dragleave', (event) => {
  if (!event.relatedTarget) drop.classList.remove('dropping');
});
document.addEventListener('drop', (event) => {
  const transfer = event.dataTransfer;
  if (!supportedDrop(transfer)) return;
  event.preventDefault();
  drop.classList.remove('dropping');
  if (!connected || disconnecting) return;
  if (transfer.files.length) { sendFiles(Array.from(transfer.files)); return; }
  const uri = transfer.getData('text/uri-list').split(/\r?\n/).find((line) => line.trim() && !line.startsWith('#'));
  const value = (uri || transfer.getData('text/plain')).trim();
  if (value.length > 8192) { notice(linkStatus, 'This website address is too long.'); return; }
  link.value = value;
  sendLink();
});

setInterval(() => { if (connected && !document.hidden) refresh(); }, 2000);
document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(); });
refresh();
