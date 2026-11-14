#!/usr/bin/env node
// Capture a real page, pass it through production Java storage, and read it without a server or JavaScript.
const fs = require('fs'), path = require('path'), os = require('os'), http = require('http');
const assert = require('assert/strict'), { spawnSync } = require('child_process'), { pathToFileURL } = require('url');
const root = path.resolve(__dirname, '..');
const puppeteer = require(path.join(root, '.tools/markdown-renderer/node_modules/puppeteer-core'));

(async () => {
  const temporary = fs.mkdtempSync(path.join(os.tmpdir(), 'scriflow-web-archive-'));
  const output = path.join(root, 'artifacts/websites');
  fs.mkdirSync(output, { recursive: true });
  let browser, server;
  try {
    server = http.createServer((request, response) => {
      if (request.url === '/style.css') {
        response.setHeader('Content-Type', 'text/css');
        response.end('body{background:white;color:black;font:20px sans-serif;padding:36px} h1{border-bottom:3px solid black} #saved-image{width:60px;height:60px}');
      } else if (request.url === '/image.svg') {
        response.setHeader('Content-Type', 'image/svg+xml');
        response.end('<svg xmlns="http://www.w3.org/2000/svg" width="60" height="60"><rect width="60" height="60" fill="black"/><circle cx="30" cy="30" r="18" fill="white"/></svg>');
      } else if (request.url === '/frame') {
        response.setHeader('Content-Type', 'text/html'); response.end('<p>Archived frame content</p>');
      } else {
        response.setHeader('Content-Type', 'text/html; charset=utf-8');
        response.end('<!doctype html><html><head><title>Offline quantum notes</title><link rel="stylesheet" href="/style.css"></head>'
          + '<body><h1>Offline quantum notes</h1><p id="physics">The state evolves under the Hamiltonian.</p>'
          + '<img id="saved-image" alt="Saved illustration" src="/image.svg"><p id="scripted"></p><iframe src="/frame"></iframe>'
          + '<img id="inline-image" alt="Inline illustration" src="data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24"><circle cx="12" cy="12" r="10" fill="black"/></svg>') + '">'
          + '<p><a href="/next">Another page needs its own save</a></p>'
          + '<script>window.liveRuns=1;document.getElementById("scripted").textContent="Loaded while online";</script></body></html>');
      }
    });
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    browser = await puppeteer.launch({ executablePath: process.env.CHROME_BIN || '/usr/bin/google-chrome', headless: true,
      args: ['--no-sandbox', '--disable-dev-shm-usage', '--disable-background-networking'] });
    const live = await browser.newPage();
    await live.goto(`http://127.0.0.1:${server.address().port}/article`, { waitUntil: 'networkidle0' });
    const session = await live.createCDPSession();
    const { data } = await session.send('Page.captureSnapshot', { format: 'mhtml' });
    const original = path.join(temporary, 'captured.mht'); fs.writeFileSync(original, data);
    await live.close();
    await new Promise(resolve => server.close(resolve)); server = null;
    // A new process/profile prevents the online page's HTTP cache from helping the reader.
    await browser.close();
    browser = await puppeteer.launch({ executablePath: process.env.CHROME_BIN || '/usr/bin/google-chrome', headless: true,
      args: ['--no-sandbox', '--disable-dev-shm-usage', '--disable-background-networking'] });

    const stored = spawnSync('java', ['-ea', '-cp', path.join(root, 'build/host-tests'),
      'org.eink.inkprobe.notebook.NotebookWebsiteArchiveFixture', temporary, original], { encoding: 'utf8' });
    assert.equal(stored.status, 0, stored.stderr);
    const archive = stored.stdout.trim();
    assert.deepEqual(fs.readFileSync(archive), fs.readFileSync(original), 'storage preserves the complete archive');
    const offline = await browser.newPage();
    await offline.setViewport({ width: 800, height: 1000, deviceScaleFactor: 1 });
    await offline.setJavaScriptEnabled(false); await offline.setOfflineMode(true);
    const blocked = [];
    await offline.setRequestInterception(true);
    offline.on('request', request => {
      if (/^https?:/.test(request.url())) { blocked.push(request.url()); request.abort(); }
      else request.continue();
    });
    await offline.goto(pathToFileURL(archive).href, { waitUntil: 'load' });
    assert.equal(await offline.title(), 'Offline quantum notes', 'title survives offline reopening');
    assert.equal(await offline.$eval('#physics', node => node.textContent), 'The state evolves under the Hamiltonian.');
    assert.equal(await offline.$eval('#scripted', node => node.textContent), 'Loaded while online', 'rendered DOM survives without running scripts');
    assert.equal(await offline.evaluate(() => window.liveRuns), undefined, 'saved scripts never run');
    assert.equal(await offline.$eval('h1', node => getComputedStyle(node).borderBottomWidth), '3px', 'external CSS is archived');
    assert.equal(await offline.$eval('#saved-image', node => node.complete && node.naturalWidth === 60), true, 'external image is archived');
    assert.equal(await offline.$eval('#inline-image', node => node.complete && node.naturalWidth === 24), true, 'inline image is preserved');
    const frame = offline.frames().find(frame => frame !== offline.mainFrame());
    assert.ok(frame && (await frame.$eval('p', node => node.textContent)) === 'Archived frame content', 'frame contents are archived');
    // Chromium can emit original resource URLs even when their bytes come from MHTML.
    // Abort those routes and disable the network as well; successful rendering must
    // therefore come from the archive, with no live server or previous browser cache.
    assert.equal(await offline.evaluate(() => navigator.onLine), false, 'network remains disabled throughout offline reading');
    await offline.screenshot({ path: path.join(output, 'offline-page.png'), fullPage: true });
    console.log('Website archive: 10 checks passed (real Chromium capture, production storage, fresh browser, offline text/CSS/images/frame, disabled scripts; ' + blocked.length + ' network routes blocked).');
  } finally {
    if (browser) await browser.close();
    if (server) await new Promise(resolve => server.close(resolve));
    fs.rmSync(temporary, { recursive: true, force: true });
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
