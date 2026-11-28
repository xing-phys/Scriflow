#!/usr/bin/env node
// Bundled page and production server in desktop and mobile Chromium.
const assert = require('node:assert/strict');
const { spawn } = require('node:child_process');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const readline = require('node:readline');
const root = path.join(__dirname, '..');
const puppeteer = require(path.join(root, '.tools/markdown-renderer/node_modules/puppeteer-core'));

async function run() {
  const scratch = await fs.mkdtemp(path.join(os.tmpdir(), 'scriflow-share-page-'));
  let server, browser;
  let step = 'serving the page';
  try {
    server = spawn(process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin/java') : 'java',
      ['-cp', 'build/share-tests', 'org.eink.inkprobe.notebook.NotebookShareServerTest', '--browser'],
      { cwd: root, stdio: ['pipe', 'pipe', 'inherit'] });
    const lines = readline.createInterface({ input: server.stdout });
    const address = await new Promise((resolve, reject) => {
      lines.once('line', resolve);
      server.once('error', reject);
      server.once('exit', (code) => reject(new Error('Sharing fixture exited: ' + code)));
    });
    browser = await puppeteer.launch({
      executablePath: process.env.CHROME_BIN || '/usr/bin/google-chrome', headless: true,
      args: ['--no-sandbox', '--disable-dev-shm-usage', '--disable-background-networking'],
    });
    const page = await browser.newPage();
    page.setDefaultTimeout(10000);
    await page.setViewport({ width: 1280, height: 900 });
    const unexpected = [];
    page.on('pageerror', (error) => unexpected.push(error.message));
    page.on('request', (request) => { if (!request.url().startsWith(address)) unexpected.push(request.url()); });
    await page.goto(address);
    step = 'pairing the desktop browser';
    assert.equal(await page.$eval('#sharing', (element) => element.hidden), true);
    await page.type('#code', 'WRONG123');
    await page.click('#connect');
    await page.waitForFunction(() => document.getElementById('pair-status').textContent === 'Incorrect pairing code.');
    await page.$eval('#code', (element) => { element.value = 'abcd2345'; });
    await page.click('#connect');
    await page.waitForSelector('#incoming li:nth-child(2)', { visible: true });
    assert.equal(await page.$eval('#incoming a[target="_blank"]', (element) => element.href),
      'https://example.org/a%2Fb?q=1#tablet');
    assert.ok((await browser.defaultBrowserContext().cookies()).some(
      (cookie) => cookie.name === 'scriflow_share' && cookie.httpOnly && cookie.sameSite === 'Strict'));

    step = 'downloading the offered file';
    const client = await page.createCDPSession();
    await client.send('Page.setDownloadBehavior', { behavior: 'allow', downloadPath: scratch });
    await page.click('#incoming a[download]');
    const downloaded = path.join(scratch, '研究 notes #1.bin');
    let bytes = 0;
    for (let attempt = 0; attempt < 40 && bytes !== 200000; attempt++) {
      try { bytes = (await fs.stat(downloaded)).size; } catch (error) { if (error.code !== 'ENOENT') throw error; }
      if (bytes !== 200000) await new Promise((resolve) => setTimeout(resolve, 50));
    }
    assert.equal(bytes, 200000, 'Download preserves the offered name and length');

    step = 'uploading selected and dropped files';
    const selected = path.join(scratch, '新 lecture.txt');
    await fs.writeFile(selected, 'from the browser\n');
    await (await page.$('#files')).uploadFile(selected);
    await page.waitForFunction(() => document.getElementById('file-status').textContent.includes('新 lecture.txt'));
    await page.waitForFunction(() => document.getElementById('file-status').textContent.startsWith('Saved to tablet Downloads'));
    await page.evaluate(() => {
      const data = new DataTransfer();
      data.items.add(new File([], 'empty.bin', { type: 'application/octet-stream' }));
      document.getElementById('drop').dispatchEvent(new DragEvent('drop', { bubbles: true, cancelable: true, dataTransfer: data }));
    });
    await page.waitForFunction(() => document.getElementById('file-status').textContent === 'Saved to tablet Downloads as empty.bin');

    step = 'sending pasted and dropped URLs';
    await page.type('#link', 'https://example.org/copied?test=1');
    await page.click('#send-link');
    await page.waitForFunction(() => document.getElementById('link-status').textContent === 'Link sent to the tablet.');
    await page.evaluate(() => {
      const data = new DataTransfer();
      data.setData('text/uri-list', '# a web address\r\nhttps://example.org/dropped#section');
      document.body.dispatchEvent(new DragEvent('drop', { bubbles: true, cancelable: true, dataTransfer: data }));
    });
    await page.waitForFunction(() => document.getElementById('link').value === ''
      && document.getElementById('link-status').textContent === 'Link sent to the tablet.');
    assert.equal(page.url(), address);
    await page.screenshot({ path: path.join(root, 'build/wifi-sharing-desktop.png') });
    await page.reload();
    await page.waitForSelector('#incoming a[download]', { visible: true });

    step = 'pairing and disconnecting the mobile browser';
    const mobileContext = await browser.createBrowserContext();
    const phone = await mobileContext.newPage();
    await phone.setViewport({ width: 320, height: 760, isMobile: true, hasTouch: true });
    await phone.goto(address);
    assert.equal(await phone.$eval('#sharing', (element) => element.hidden), true);
    await phone.type('#code', 'ABCD2345');
    await phone.click('#connect');
    await phone.waitForSelector('#incoming a[download]', { visible: true });
    assert.equal(await phone.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    await phone.screenshot({ path: path.join(root, 'build/wifi-sharing-phone.png'), fullPage: true });
    await phone.click('#disconnect');
    await phone.waitForSelector('#code', { visible: true });
    await phone.reload();
    assert.equal(await phone.$eval('#sharing', (element) => element.hidden), true);
    assert.equal(await page.$eval('#sharing', (element) => element.hidden), false);
    assert.deepEqual(unexpected, []);
    console.log('Wi-Fi sharing page: pairing, uploads, download, links, and mobile browser checks passed');
  } catch (error) {
    throw new Error('Error while ' + step, { cause: error });
  } finally {
    if (browser) await browser.close();
    if (server && server.exitCode === null) {
      const ended = new Promise((resolve) => server.once('exit', resolve));
      server.stdin.end('stop\n');
      await ended;
    }
    await fs.rm(scratch, { recursive: true, force: true });
  }
}

run().catch((error) => { console.error(error); process.exitCode = 1; });
