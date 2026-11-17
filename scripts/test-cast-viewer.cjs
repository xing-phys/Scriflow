#!/usr/bin/env node
// Real Chromium, production Java server and bundled viewer; no external network.
const fs = require('fs'), path = require('path'), assert = require('assert/strict');
const {spawn} = require('child_process'), readline = require('readline');
const root = path.resolve(__dirname, '..');
const puppeteer = require(path.join(root, '.tools/markdown-renderer/node_modules/puppeteer-core'));
const password = 'Quantum 中文 + & = password';
(async () => {
  let browser, server, checks = 0;
  const ok = (condition, text) => { assert.ok(condition, text); checks++; };
  try {
    browser = await puppeteer.launch({executablePath: process.env.CHROME_BIN || '/usr/bin/google-chrome', headless: true,
      args: ['--no-sandbox', '--disable-dev-shm-usage', '--disable-background-networking']});
    const fixture = await browser.newPage(); await fixture.setViewport({width: 750, height: 1000});
    await fixture.setContent('<body style="margin:40px;font:24px Georgia;background:white;color:black"><h1>Quantum dynamics</h1><hr><p>Schrödinger evolution</p><p>iℏ ∂ₜ |ψ⟩ = H |ψ⟩</p><p>Scriflow screen cast</p></body>');
    await fixture.screenshot({path: path.join(root, 'build/cast-viewer-fixture.jpg'), type: 'jpeg'}); await fixture.close();
    const next = await browser.newPage(); await next.setViewport({width: 750, height: 1000});
    await next.setContent('<body style="font:32px Georgia;background:white;color:black;padding:40px"><h1>Lecture · next page</h1><p>New frames arrive without a polling timer.</p></body>');
    await next.screenshot({path: path.join(root, 'build/cast-viewer-next.jpg'), type: 'jpeg'}); await next.close();
    const black = await browser.newPage(); await black.setViewport({width: 750, height: 1000});
    await black.setContent('<body style="margin:0;background:black"></body>');
    await black.screenshot({path: path.join(root, 'build/cast-viewer-black.jpg'), type: 'jpeg'}); await black.close();
    fs.writeFileSync(path.join(root, 'build/cast-viewer-corrupt.jpg'), 'incomplete image');
    server = spawn(process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin/java') : 'java',
      ['-cp', 'build/cast-tests', 'org.eink.inkprobe.notebook.NotebookCastServerTest', '--viewer', 'build/cast-viewer-fixture.jpg'], {cwd: root, stdio: ['pipe', 'pipe', 'inherit']});
    const lines = readline.createInterface({input: server.stdout});
    const address = await new Promise((resolve, reject) => { lines.once('line', resolve); server.once('error', reject); server.once('exit', code => reject(new Error('Fixture exited: ' + code))); });
    const first = await browser.createBrowserContext(), second = await browser.createBrowserContext();
    const page = await first.newPage(); await page.setViewport({width: 1280, height: 980});
    await page.evaluateOnNewDocument(() => {
      const decode = HTMLImageElement.prototype.decode;
      HTMLImageElement.prototype.decode = async function() {
        await decode.call(this);
        if (window.holdFrameDecode) { window.frameDecodeWaiting = true; await new Promise(resolve => { window.releaseFrameDecode = resolve; }); }
      };
    });
    const errors = [], external = [];
    page.on('pageerror', error => errors.push(error.message));
    page.on('request', request => { if (!request.url().startsWith(address) && !request.url().startsWith('blob:')) external.push(request.url()); });
    await page.goto(address);
    ok(await page.$eval('#login', el => !el.hidden), 'Unauthenticated browser sees only the login form');
    ok(await page.$eval('#screen', el => el.hidden && !el.hasAttribute('src')), 'Password gate has no screen pixels');
    await page.type('#password', 'incorrect'); await page.click('#connect');
    await page.waitForFunction(() => document.querySelector('#status').textContent === 'Incorrect password.'); checks++;
    await page.type('#password', password); await page.click('#connect');
    await page.waitForFunction(() => { const image = document.querySelector('#screen'); return !image.hidden && image.complete && image.naturalWidth === 750; }); checks++;
    ok(await page.$eval('#password', el => el.value === ''), 'Successful login clears the password field');
    ok((await first.cookies()).some(cookie => cookie.name === 'scriflow_cast' && cookie.httpOnly && cookie.sameSite === 'Strict'), 'Real browser accepts private same-site sessions');
    await page.screenshot({path: path.join(root, 'build/cast-viewer-desktop.png')});
    const before = await page.$eval('#screen', el => el.src);
    await page.evaluate(() => { window.holdFrameDecode = true; });
    server.stdin.write('image build/cast-viewer-next.jpg\n');
    await page.waitForFunction(() => window.frameDecodeWaiting); checks++;
    await new Promise(resolve => setTimeout(resolve, 160));
    ok(await page.$eval('#screen', (el, old) => !el.hidden && el.complete && el.src === old, before), 'Slow decoding leaves the previous complete image visible');
    await page.evaluate(() => { window.holdFrameDecode = false; window.releaseFrameDecode(); });
    await page.waitForFunction(old => document.querySelector('#screen').src !== old, {}, before); checks++;
    ok(await page.$eval('#screen', el => !el.hidden && el.complete && el.naturalWidth === 750), 'The replacement becomes visible only after decoding');
    const good = await page.$eval('#screen', el => el.src);
    server.stdin.write('image build/cast-viewer-corrupt.jpg\n');
    await page.waitForFunction(() => document.querySelector('#status').textContent === 'Recovering the next frame…');
    ok(await page.$eval('#screen', (el, old) => !el.hidden && el.src === old, good), 'An incomplete image cannot blank a lecture frame');
    server.stdin.write('hold\n');
    await new Promise(resolve => setTimeout(resolve, 120));
    ok(await page.$eval('#screen', el => !el.hidden && el.complete), 'A safe app-popup focus handoff retains the displayed frame');
    server.stdin.write('resume\n');
    await page.waitForFunction(() => document.querySelector('#status').textContent === 'Live · View only');
    const beforeBlack = await page.$eval('#screen', el => el.src);
    const blackBytes = fs.statSync(path.join(root, 'build/cast-viewer-black.jpg')).size;
    const blackArrival = page.waitForResponse(response => response.url().endsWith('/frame') && Number(response.headers()['content-length']) === blackBytes);
    server.stdin.write('image build/cast-viewer-black.jpg\n'); await blackArrival;
    await new Promise(resolve => setTimeout(resolve, 60));
    ok(await page.$eval('#screen', (el, old) => !el.hidden && el.src === old, beforeBlack), 'A short all-black compositor frame cannot replace a lecture page');
    server.stdin.write('resume\n');
    await page.waitForFunction(old => document.querySelector('#screen').src !== old, {}, beforeBlack);
    const beforePersistentBlack = await page.$eval('#screen', el => el.src);
    const blackStart = Date.now(); server.stdin.write('image build/cast-viewer-black.jpg\n');
    await page.waitForFunction(old => document.querySelector('#screen').src !== old, {}, beforePersistentBlack);
    ok(Date.now()-blackStart >= 220, 'Persistent black content remains supported after a short confirmation');
    server.stdin.write('resume\n');
    await page.waitForFunction(old => document.querySelector('#screen').src !== old, {}, await page.$eval('#screen', el => el.src));
    let idleRequests = 0;
    const count = request => { if (request.url().endsWith('/frame')) idleRequests++; };
    page.on('request', count); await new Promise(resolve => setTimeout(resolve, 1300)); page.off('request', count);
    ok(idleRequests <= 3, 'Static lecture pages use a waiting request instead of repeatedly polling');
    await page.reload();
    await page.waitForFunction(() => !document.querySelector('#screen').hidden); checks++;
    const phone = await second.newPage(); await phone.setViewport({width: 390, height: 844, isMobile: true, hasTouch: true});
    await phone.goto(address); ok(await phone.$eval('#login', el => !el.hidden), 'Another browser must enter its own password');
    await phone.type('#password', password); await phone.click('#connect');
    await phone.waitForFunction(() => !document.querySelector('#screen').hidden); checks++;
    ok(await phone.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'Mobile viewer fits without horizontal scrolling');
    await phone.screenshot({path: path.join(root, 'build/cast-viewer-phone.png')});
    await phone.click('#logout'); await phone.waitForFunction(() => !document.querySelector('#login').hidden); checks++;
    await page.bringToFront(); await page.waitForFunction(() => !document.querySelector('#screen').hidden); checks++;
    server.stdin.write('pause\n');
    await page.waitForFunction(() => document.querySelector('#screen').hidden && document.querySelector('#status').textContent.startsWith('Cast paused.')); checks++;
    ok(await page.$eval('#screen', el => !el.hasAttribute('src')), 'Pause removes the image URL');
    server.stdin.write('resume\n'); await page.waitForFunction(() => !document.querySelector('#screen').hidden); checks++;
    await page.click('#logout'); await page.waitForFunction(() => !document.querySelector('#login').hidden); checks++;
    await page.reload(); ok(await page.$eval('#login', el => !el.hidden), 'Disconnected browser stays signed out on reload');
    await page.type('#password', password); await page.click('#connect');
    await page.waitForFunction(() => !document.querySelector('#screen').hidden);
    server.stdin.write('stop\n');
    await page.waitForFunction(() => document.querySelector('#screen').hidden && document.querySelector('#status').textContent.startsWith('Connection lost.')); checks++;
    ok(errors.length === 0 && external.length === 0, 'Viewer has no JavaScript failures or external requests');
    console.log('Cast viewer: ' + checks + ' browser checks passed; desktop/mobile screenshots in build/.');
  } finally {
    if (server && server.exitCode === null) server.kill();
    if (browser) await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
