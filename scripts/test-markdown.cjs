#!/usr/bin/env node
// Exercise the exact offline assets packaged by Gradle, using a real browser engine.
const fs = require('fs'), path = require('path'), http = require('http'), assert = require('assert');
const root = path.resolve(__dirname, '..');
const puppeteer = require(path.join(root, '.tools/markdown-renderer/node_modules/puppeteer-core'));
const assets = path.join(root, 'app/build/generated/markdown-assets/markdown');
const output = path.join(root, 'artifacts/markdown-blocks');
const sample = String.raw`# Quantum dynamics

The **state** obeys $i\hbar\partial_t\ket{\psi}=H(t)\ket{\psi}$ and has *unit norm*.

$$\begin{aligned}H(t)&=\sum_{j=1}^N J_j\sigma_j^z\sigma_{j+1}^z+g(t)\sum_j\sigma_j^x\\U(t)&=\mathcal T\exp\left[-\frac{i}{\hbar}\int_0^t H(s)\,ds\right]\end{aligned}$$

| Observable | Definition |
| :-- | --: |
| Energy | $\langle H\rangle$ |
| Purity | $\operatorname{Tr}\rho^2$ |

- [x] Preserve **source** and rendered formulas.
- [ ] Test vector export.
- Parent list
  - Nested item

1. First step
2. Second step

> [!NOTE]
> A callout with $\alpha+\beta$.

::: tip
Keep the original Markdown.[^source]
:::

Term
: A definition with H~2~O and x^2^.

[^source]: Offline and editable.

---

<details><summary>Details</summary>Safe HTML.</details>

` + '```julia\nU = exp(-im * H * t)\n```\n\n```mermaid\ngraph LR\n  A[Initial state] --> B[Unitary evolution]\n  B --> C[Measurement]\n```';

(async () => {
  fs.mkdirSync(output, { recursive: true });
  const missing = [], egress = [], errors = [];
  const server = http.createServer((req, res) => {
    const file = path.resolve(assets, '.' + decodeURIComponent(req.url.split('?')[0]));
    if (!file.startsWith(assets + path.sep)) { res.writeHead(403); res.end(); return; }
    try {
      res.setHeader('Content-Type', ({ '.js':'text/javascript', '.css':'text/css', '.html':'text/html', '.ttf':'font/ttf', '.otf':'font/otf' })[path.extname(file)] || 'application/octet-stream');
      res.end(fs.readFileSync(file));
    } catch { if (!file.endsWith('favicon.ico')) missing.push(req.url); res.writeHead(404); res.end(); }
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = `http://127.0.0.1:${server.address().port}`;
  const browser = await puppeteer.launch({ executablePath: process.env.CHROME_BIN || '/usr/bin/google-chrome', headless:true,
    args:['--no-sandbox', '--disable-dev-shm-usage'] });
  try {
    const page = await browser.newPage();
    await page.setViewport({ width:800, height:1200, deviceScaleFactor:2 });
    await page.setRequestInterception(true);
    page.on('request', request => { if (!request.url().startsWith(origin) && !request.url().startsWith('data:')) { egress.push(request.url()); request.abort(); } else request.continue(); });
    page.on('pageerror', error => errors.push(error.message));
    page.on('console', message => { if (message.type()==='error' && !message.text().includes('404')) errors.push(message.text()); });
    await page.goto(origin + '/index.html', { waitUntil:'networkidle0' });
    await page.waitForFunction('window.ScriflowMarkdown', { timeout:30000 });
    await page.evaluate(() => {
      window.appearances = [];
      const create = pdfMake.createPdf.bind(pdfMake);
      pdfMake.createPdf = function(definition, ...args) {
        if (definition.content[0].svg) window.appearances.push({ svg:new XMLSerializer().serializeToString(definition.content[0].svg), html:document.getElementById('block').innerHTML });
        try{return create(definition, ...args);}catch(error){console.error(error.stack);throw error;}
      };
    });
    async function render(name, source, allowMathError=false) {
      console.log('Rendering:',name);
      await page.evaluate(source => { window.ScriflowMarkdown.render(source, 640); }, source);
      try { await page.waitForFunction("['ready','error'].includes(window.ScriflowMarkdown.info().phase)", { timeout:60000 }); }
      catch(error) { console.error({ name,missing,egress,errors,state:await page.evaluate(()=>ScriflowMarkdown.info()),html:await page.$eval('#block',n=>n.innerHTML.slice(0,1500)) });throw error; }
      const info = await page.evaluate(() => window.ScriflowMarkdown.info());
      if(info.phase==='error') {
        const captures=await page.evaluate(()=>window.appearances);
        for(let i=0;i<captures.length;i++)fs.writeFileSync(path.join(output,`${name}-failed-${i}.svg`),captures[i].svg);
      }
      assert.equal(info.phase, 'ready', `${name}: ${info.message}`);
      const data = await page.evaluate(() => ({ rendered:ScriflowMarkdown.chunk(false,0,1e8), source:ScriflowMarkdown.chunk(true,0,1e8), captures:window.appearances.splice(0) }));
      assert.equal(data.captures.length,2);
      for (const type of ['rendered','source']) fs.writeFileSync(path.join(output, `${name}-${type}.pdf`), Buffer.from(data[type], 'base64'));
      fs.writeFileSync(path.join(output, `${name}.svg`), data.captures[0].svg);
      if(!allowMathError)assert(!data.captures[0].html.includes('data-mjx-error'), `${name}: ${info.warning}`);
      return { info, ...data };
    }
    const main = await render('markdown',sample);
    assert.equal(main.info.warning,'',main.info.warning);
    assert(main.captures[0].html.includes('mjx-container'));
    assert(main.captures[0].html.includes('md-marker'));
    assert(main.captures[0].html.includes('md-checkbox'));
    assert(main.captures[0].html.includes('flowchart'));
    assert(main.captures[0].html.includes('footnote'));
    const math = await render('math',String.raw`# Mathematics

Inline $\frac{\partial \rho}{\partial t}=-i[H,\rho]$, \(\mathbb C^2\otimes\mathbb C^2\), $\ce{H2O}$, and $\dv{f}{t}$.

\[\begin{pmatrix}a&b\\c&d\end{pmatrix}\quad\bra{\phi}\hat H\ket{\psi}\quad\sum_{n=0}^{\infty}\frac{x^n}{n!}\]

\begin{equation}E=mc^2\label{eq:energy}\end{equation}

Reference $\eqref{eq:energy}$.

` + '```math\n\\int_{-\\infty}^\\infty e^{-x^2}\\,dx=\\sqrt{\\pi}\n```');
    assert.equal(math.info.warning,'',math.info.warning);
    const cjk = await render('cjk', '# 量子动力学\n\n中文 **粗体**，行内公式 $\\hbar$。English 日本語 한국어。');
    assert.equal(cjk.info.warning,'');
    assert(cjk.captures[0].svg.includes('ScriflowCJK'));
    const unsafe = await render('sanitized','<script>window.compromised=true</script><img src="https://invalid.example/image.png" onerror="window.compromised=true">\n\n[unsafe](javascript:alert(1))');
    assert.equal(await page.evaluate(() => !!window.compromised),false);
    assert(!unsafe.captures[0].html.includes('onerror'));
    assert(unsafe.captures[0].html.includes('embedded image required'));
    const bad = await render('invalid-math', '$\\notARealCommand{x}$',true);
    assert(bad.info.warning.includes('Math:'));
    assert.deepEqual(missing,[],`Missing local assets: ${missing}`);
    assert.deepEqual(egress,[],`Unexpected external request: ${egress}`);
    assert.deepEqual(errors,[],`Browser errors: ${errors}`);
    fs.writeFileSync(path.join(output,'renderer-qa.json'),JSON.stringify({ cases:5, main:main.info, math:math.info, cjk:cjk.info, missing,egress,errors },null,2));
    console.log('Offline Markdown renderer: 5 browser cases passed; math, CJK, diagrams, source snapshots, sanitization and zero network egress.');
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
})().catch(error => { console.error(error); process.exitCode=1; });
