#!/usr/bin/env node
// Freeze a rendered document using production capture, then reopen with no server/cache/scripts.
const fs = require('fs'), path = require('path'), os = require('os'), http = require('http');
const assert = require('assert/strict'), { spawnSync } = require('child_process'), { pathToFileURL } = require('url');
const root = path.resolve(__dirname, '..');
const puppeteer = require(path.join(root, '.tools/markdown-renderer/node_modules/puppeteer-core'));
const { allowedRequest } = require(path.join(root, 'build/browser-test-assets/policy.cjs'));
const katex = path.join(root, '.tools/markdown-renderer/node_modules/katex/dist');

(async () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'scriflow-html-capture-'));
  let browser, server, checks = 0;
  const equal = (a, b, message) => { assert.deepEqual(a, b, message); checks++; };
  const ok = (a, message) => { assert.ok(a, message); checks++; };
  try {
    const own = 'moz-extension://owned/', archive = 'file:///private/websites/selected/page.html';
    for (const url of ['https://example.org/', 'wss://example.org/socket', 'data:image/png;base64,AA==', 'blob:https://example.org/id'])
      equal(allowedRequest(url, { offline: false }, own), true, 'online standard secure and embedded resources');
    for (const url of ['http://example.org/', 'ws://example.org/', 'file:///private/secret', 'content://private/item'])
      equal(allowedRequest(url, { offline: false }, own), false, 'online policy rejects cleartext and local files');
    for (const url of [archive, archive + '#section', 'data:image/png;base64,AA==', 'about:srcdoc'])
      equal(allowedRequest(url, { offline: true, archive }, own), true, 'offline policy permits only archive and embedded bytes');
    for (const url of ['https://example.org/', 'wss://example.org/', 'file:///private/websites/other/page.html', archive + '?remote=1', 'file:///private/secret'])
      equal(allowedRequest(url, { offline: true, archive }, own), false, 'offline requests never fall back to network or other files');
    equal(allowedRequest('https://example.org/', undefined, own), false, 'an unconfigured session cannot request content');
    equal(allowedRequest(own + 'bootstrap.html', undefined, own), true, 'trusted bootstrap can configure a new session');
    equal(allowedRequest('moz-extension://other/bootstrap.html', undefined, own), false, 'other extensions are outside bootstrap scope');
    server = http.createServer((request, response) => {
      if (request.url === '/style.css') {
        response.setHeader('Content-Type', 'text/css');
        response.end('@import url("/import.css") layer(saved) supports(display: grid) screen; body{background:white;color:black;font:20px sans-serif;padding:30px} h1{border-bottom:3px solid black} #saved-image{width:60px;height:60px} #background{width:60px;height:60px;background-image:url("/image.svg")}');
      } else if (request.url === '/import.css') {
        response.setHeader('Content-Type', 'text/css'); response.end('#physics{border-left:7px solid black}');
      } else if (request.url === '/katex.css') {
        response.setHeader('Content-Type', 'text/css'); response.end(fs.readFileSync(path.join(katex, 'katex.min.css')));
      } else if (/^\/fonts\/KaTeX_[\w-]+\.(woff2?|ttf)$/.test(request.url)) {
        response.setHeader('Content-Type', request.url.endsWith('woff2') ? 'font/woff2' : request.url.endsWith('woff') ? 'font/woff' : 'font/ttf');
        response.end(fs.readFileSync(path.join(katex, request.url)));
      } else if (request.url === '/image.svg') {
        response.setHeader('Content-Type', 'image/svg+xml'); response.end('<svg xmlns="http://www.w3.org/2000/svg" width="60" height="60"><rect width="60" height="60" fill="black"/></svg>');
      } else if (request.url === '/frame') {
        response.setHeader('Content-Type', 'text/html'); response.end('<p>Frozen frame content</p><script>window.frameRuns=1</script>');
      } else if (request.url === '/missing.svg') { response.writeHead(404); response.end('missing'); }
      else {
        response.setHeader('Content-Type', 'text/html; charset=utf-8');
        response.end('<!doctype html><html><head><meta charset="utf-8"><title>Offline math snapshot</title><link rel="stylesheet" href="/style.css"><link rel="stylesheet" href="/katex.css"></head><body>'
          + '<h1>Offline quantum notes</h1><p id="physics">物理 · The state evolves under the Hamiltonian.</p><p id="formula" class="math"></p>'
          + '<img id="saved-image" src="/image.svg" srcset="/missing.svg 2x"><div id="background"></div><img id="unavailable" src="/missing.svg">'
          + '<canvas id="drawing" width="30" height="30"></canvas><iframe src="/frame"></iframe><p id="scripted"></p>'
          + '<input id="secret" type="password" value="do-not-save"><input id="value" value="old"><textarea id="text">old</textarea>'
          + '<a id="unsafe" href="javascript:alert(1)" onclick="window.attack=1">Unsafe</a><a id="external" target="_blank" href="https://example.org/other">External</a>'
          + '<p><a id="first" href="#section-one">First section</a> · <a id="second" href="#section-two">Second section</a></p>'
          + '<div style="height:1200px"></div><h2 id="section-one">First saved section</h2><div style="height:1200px"></div><h2 id="section-two">Second saved section</h2><div style="height:1000px"></div>'
          + '<noscript>This must not replace the rendered page.</noscript><script>window.liveRuns=1;document.getElementById("scripted").textContent="Rendered online";</script></body></html>');
      }
    });
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    const launch = () => puppeteer.launch({ executablePath: process.env.CHROME_BIN || '/usr/bin/google-chrome', headless: true,
      args: ['--no-sandbox', '--disable-dev-shm-usage', '--disable-background-networking'] });
    browser = await launch();
    const live = await browser.newPage(); await live.setViewport({ width: 800, height: 1000 });
    await live.goto(`http://127.0.0.1:${server.address().port}/article`, { waitUntil: 'networkidle0' });
    await live.addScriptTag({ path: path.join(katex, 'katex.min.js') });
    await live.evaluate(() => {
      katex.render(String.raw`i\hbar\frac{\partial}{\partial t}|\psi\rangle = H|\psi\rangle`, document.querySelector('#formula'));
      document.querySelector('#value').value = 'Edited online'; document.querySelector('#text').value = 'Frozen text';
      document.querySelector('#drawing').getContext('2d').fillRect(0, 0, 30, 30);
    });
    await live.addScriptTag({ path: path.join(root, 'build/browser-test-assets/capture.js') });
    const result = await live.evaluate(() => ScriflowCapture.captureDocument(document, async url => {
      const response = await fetch(url); if (!response.ok) throw Error('HTTP ' + response.status);
      const data = new Uint8Array(await response.arrayBuffer()); let binary = '';
      for (let i = 0; i < data.length; i += 32768) binary += String.fromCharCode(...data.subarray(i, i + 32768));
      return { data: btoa(binary), mime: response.headers.get('content-type'), url: response.url };
    }));
    equal(result.missing, 1, 'one unavailable image is reported, not silently claimed as captured');
    ok(result.html.includes('data:font/woff2;base64,'), 'math fonts are embedded');
    const original = path.join(directory, 'captured.html'); fs.writeFileSync(original, result.html);
    await browser.close(); browser = null; await new Promise(resolve => server.close(resolve)); server = null;
    const stored = spawnSync('java', ['-ea', '-cp', path.join(root, 'build/host-tests'),
      'org.eink.inkprobe.notebook.NotebookWebsiteArchiveFixture', directory, original, 'html'], { encoding: 'utf8' });
    equal(stored.status, 0, stored.stderr); const archivePath = stored.stdout.trim();
    equal(fs.readFileSync(archivePath), fs.readFileSync(original), 'production storage preserves the complete HTML snapshot');
    browser = await launch(); const offline = await browser.newPage();
    await offline.setViewport({ width: 800, height: 1000 }); await offline.setJavaScriptEnabled(false); await offline.setOfflineMode(true);
    const network = []; await offline.setRequestInterception(true);
    offline.on('request', request => { if (/^https?:/.test(request.url())) { network.push(request.url()); request.abort(); } else request.continue(); });
    const local = pathToFileURL(archivePath).href; await offline.goto(local, { waitUntil: 'load' });
    equal(await offline.title(), 'Offline math snapshot', 'title survives cold offline reopen');
    equal(await offline.$eval('#physics', n => n.textContent), '物理 · The state evolves under the Hamiltonian.', 'UTF-8 text survives');
    equal(await offline.$eval('#scripted', n => n.textContent), 'Rendered online', 'DOM changes are frozen');
    equal(await offline.evaluate(() => window.liveRuns), undefined, 'saved scripts never execute');
    equal(await offline.$eval('h1', n => getComputedStyle(n).borderBottomWidth), '3px', 'external CSS survives');
    equal(await offline.$eval('#physics', n => getComputedStyle(n).borderLeftWidth), '7px', 'layered conditional CSS imports survive');
    equal(await offline.$eval('#saved-image', n => n.complete && n.naturalWidth), 60, 'image bytes survive');
    ok((await offline.$eval('#background', n => getComputedStyle(n).backgroundImage)).startsWith('url("data:'), 'CSS backgrounds are self contained');
    equal(await offline.$$eval('canvas', nodes => nodes.length), 0, 'canvas is replaced by readable image bytes');
    equal(await offline.$eval('img[alt="Saved canvas"]', n => n.complete && n.naturalWidth), 30, 'canvas pixels survive');
    const frame = offline.frames().find(frame => frame !== offline.mainFrame());
    equal(await frame.$eval('p', n => n.textContent), 'Frozen frame content', 'same-origin frame is frozen');
    equal(await offline.$eval('#secret', n => n.value), '', 'passwords are not saved');
    equal(await offline.$eval('#value', n => n.value), 'Edited online', 'edited form display is retained');
    equal(await offline.$eval('#text', n => n.value), 'Frozen text', 'textarea display is retained');
    equal(await offline.$eval('#unsafe', n => n.hasAttribute('href') || n.hasAttribute('onclick')), false, 'script links and handlers are stripped');
    equal(await offline.$eval('#external', n => n.getAttribute('href')), 'https://example.org/other', 'external link retains its online destination');
    equal(await offline.$$eval('script,noscript,link,base', n => n.length), 0, 'executable/loading nodes are removed');
    equal(await offline.$$eval('.katex', n => n.length), 1, 'rendered math survives with scripting disabled');
    await offline.evaluate(() => document.fonts.ready);
    equal(await offline.evaluate(() => document.fonts.check('20px KaTeX_Main')), true, 'embedded math font loads offline');
    await offline.goto(local + '#section-one'); await offline.waitForFunction(() => scrollY > 1000); const first = await offline.evaluate(() => scrollY);
    await offline.goto(local + '#section-two'); await offline.waitForFunction(first => scrollY > first + 1000, {}, first);
    await offline.goBack(); await offline.waitForFunction(first => Math.abs(scrollY - first) < 3, {}, first);
    equal(new URL(offline.url()).hash, '#section-one', 'offline Back restores a section jump');
    await offline.goForward(); equal(new URL(offline.url()).hash, '#section-two', 'offline Forward replays a section jump');
    equal(network, [], 'all snapshot resources load without any network request');
    fs.mkdirSync(path.join(root, 'artifacts/websites'), { recursive: true });
    await offline.goto(local); await offline.screenshot({ path: path.join(root, 'artifacts/websites/offline-html.png') });
    // CSP must also protect an archive if the engine accidentally enables page JavaScript.
    const cspPage = await browser.newPage(); const protectedFile = path.join(directory, 'csp.html');
    fs.writeFileSync(protectedFile, result.html.replace('</head>', '<script>window.cspFailure=1</script></head>'));
    await cspPage.goto(pathToFileURL(protectedFile).href);
    equal(await cspPage.evaluate(() => window.cspFailure), undefined, 'CSP independently blocks page scripts');
    console.log('Browser HTML capture: ' + checks + ' checks passed (production storage, cold offline math/fonts/CSS/images/canvas/frames, security policy and section history).');
  } finally {
    if (browser) await browser.close(); if (server) await new Promise(resolve => server.close(resolve));
    fs.rmSync(directory, { recursive: true, force: true });
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
