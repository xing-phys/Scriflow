import MarkdownIt from 'markdown-it';
import footnote from 'markdown-it-footnote';
import deflist from 'markdown-it-deflist';
import tasks from 'markdown-it-task-lists';
import sub from 'markdown-it-sub';
import sup from 'markdown-it-sup';
import container from 'markdown-it-container';
import texmath from 'markdown-it-texmath';
import hljs from 'highlight.js';
import mermaid from 'mermaid';
import DOMPurify from 'dompurify';
import { elementToSVG } from 'dom-to-svg';

let equations = [], diagrams = [], result = { phase: 'loading' };
const eastAsian = /[\u2e80-\u9fff\uac00-\ud7ff\uf900-\ufaff]/;
const escape = s => String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const equation = (source, display) => {
  const n = equations.push({ source, display }) - 1;
  return `<span data-equation="${n}"></span>`;
};
const md = new MarkdownIt({ html: true, linkify: true, typographer: false,
  highlight(code, lang) {
    return lang && hljs.getLanguage(lang) ? hljs.highlight(code, { language: lang, ignoreIllegals: true }).value : '';
  }
}).use(footnote).use(deflist).use(tasks, { enabled: false }).use(sub).use(sup)
  .use(texmath, { delimiters: ['dollars', 'brackets', 'beg_end'],
    engine: { renderToString: (source, options) => equation(source, !!options.displayMode) } });
md.renderer.rules.footnote_anchor=()=>''; // Static note appearances have no backlink hit targets.
// texmath's default <eq> wrappers are block elements even for inline math.
for (const rule of texmath.mergeDelimiters(['dollars', 'brackets', 'beg_end']).inline)
  md.renderer.rules[rule.name] = (tokens, n) => rule.displayMode
    ? `<span class="md-display">${equation(tokens[n].content,true)}</span>` : equation(tokens[n].content,false);
for (const rule of texmath.mergeDelimiters(['dollars', 'brackets', 'beg_end']).block)
  md.renderer.rules[rule.name] = (tokens, n) => `<div class="md-display">${equation(tokens[n].content,true)}${rule.name==='math_block_eqno'?`<span class="md-equation-number">(${escape(tokens[n].info)})</span>`:''}</div>`;
for (const type of ['note', 'tip', 'important', 'warning', 'caution', 'info', 'success', 'danger'])
  md.use(container, type, { render: (tokens, n) => tokens[n].nesting === 1
    ? `<aside class="md-callout"><p class="md-callout-title">${escape(tokens[n].info.trim())}</p>` : '</aside>' });
const fence = md.renderer.rules.fence;
md.renderer.rules.fence = (tokens, n, options, env, self) => {
  const lang = tokens[n].info.trim().toLowerCase();
  if (lang === 'mermaid') return `<div class="md-diagram" data-diagram="${diagrams.push(tokens[n].content)-1}"></div>`;
  if (['math', 'latex', 'tex'].includes(lang)) return `<eq>${equation(tokens[n].content, true)}</eq>`;
  return fence(tokens, n, options, env, self);
};
mermaid.initialize({ startOnLoad: false, securityLevel: 'strict', maxTextSize: 32768,
  maxEdges: 500, theme: 'neutral', fontFamily: 'Roboto', htmlLabels: false,
  flowchart: { htmlLabels: false }, themeVariables: { fontFamily: 'Roboto', primaryTextColor: '#111' } });
pdfMake.addFonts({
  Roboto: { normal: 'Roboto-Regular.ttf', bold: 'Roboto-Medium.ttf', italics: 'Roboto-Italic.ttf', bolditalics: 'Roboto-MediumItalic.ttf' },
  ScriflowCode: { normal: 'Code-Regular.ttf', bold: 'Code-Bold.ttf', italics: 'Code-Regular.ttf', bolditalics: 'Code-Bold.ttf' },
  ScriflowCJK: { normal: 'CJK-Regular.otf', bold: 'CJK-Bold.otf', italics: 'CJK-Regular.otf', bolditalics: 'CJK-Bold.otf' }
});

function normalize(body) {
  // The fragment is still inert: remove resource URLs before it can initiate a load.
  for(const node of body.querySelectorAll('[src],[href],[poster],[xlink\\:href]')) {
    for(const attr of ['src','href','poster','xlink:href']) {
      if(!node.hasAttribute(attr) || attr==='href' && node.tagName.toLowerCase()==='a')continue;
      const url=node.getAttribute(attr);
      if(!url.startsWith('#') && !/^data:image\/(png|jpeg|webp|gif|svg\+xml);/i.test(url))node.removeAttribute(attr);
    }
  }
  for(const detail of body.querySelectorAll('details'))detail.open=true;
  for (const image of body.querySelectorAll('img')) {
    if (!/^data:image\/(png|jpeg|webp|gif|svg\+xml);/i.test(image.getAttribute('src') || '')) {
      const alt = document.createElement('span'); alt.className = 'md-image-alt';
      alt.textContent = `[Image: ${image.getAttribute('alt') || 'embedded image required'}]`; image.replaceWith(alt);
    }
  }
  for (const quote of body.querySelectorAll('blockquote')) {
    const first = quote.querySelector('p'), text = first && first.firstChild;
    const match = text && text.nodeType === Node.TEXT_NODE && text.textContent.match(/^\[!([\w-]+)\][+-]?\s*/);
    if (match) {
      quote.className = 'md-callout'; text.textContent = text.textContent.slice(match[0].length);
      const title = document.createElement('p'); title.className = 'md-callout-title'; title.textContent = match[1];
      quote.insertBefore(title, first);
    }
  }
  for (const list of body.querySelectorAll('ul,ol')) {
    let count = Number(list.getAttribute('start') || 1);
    for (const item of Array.from(list.children).filter(n => n.tagName === 'LI')) {
      const input = Array.from(item.querySelectorAll('input[type=checkbox]')).find(input=>input.closest('li')===item);
      const mark = document.createElement('span'); mark.className = 'md-marker'; mark.setAttribute('aria-hidden', 'true');
      if (input) {
        mark.innerHTML = `<svg class="md-checkbox" width="18" height="18" viewBox="0 0 18 18" xmlns="http://www.w3.org/2000/svg"><rect x="1" y="1" width="16" height="16" fill="none" stroke="#444"/>${input.checked?'<path d="M4 9L7 12L14 5" fill="none" stroke="#111" stroke-width="2"/>':''}</svg>`;
        input.remove();
      } else {
        if (item.hasAttribute('value')) count = Number(item.getAttribute('value'));
        mark.textContent = list.tagName === 'OL' ? `${count++}.` : '•';
      }
      item.insertBefore(mark, item.firstChild);
    }
  }
}

function normalizeBaselines(svg) {
  const metrics=new Map();
  for(const text of svg.querySelectorAll('text[dominant-baseline="text-after-edge"]')) {
    const family=text.getAttribute('font-family')||'Roboto',size=text.getAttribute('font-size')||'18px',
      weight=text.getAttribute('font-weight')||'400',style=text.getAttribute('font-style')||'normal';
    const key=family+size+weight+style;
    if(!metrics.has(key)) {
      // DOM Range bottoms use the font's typographic descent. SVG-to-PDF's
      // text-after-edge uses its deepest glyph bbox, which clips CJK headings.
      // Measure the actual browser baseline, including on older tablet WebViews.
      const probe=document.createElement('span'),marker=document.createElement('span'),sample=document.createTextNode('Hg');
      Object.assign(probe.style,{position:'absolute',left:'-10000px',top:'0',whiteSpace:'pre',fontFamily:family,fontSize:size,fontWeight:weight,fontStyle:style,lineHeight:'normal'});
      Object.assign(marker.style,{display:'inline-block',width:'0',height:'0',verticalAlign:'baseline'});
      probe.append(marker,sample);document.body.append(probe);
      const range=document.createRange();range.selectNode(sample);
      metrics.set(key,range.getBoundingClientRect().bottom-marker.getBoundingClientRect().top);probe.remove();
    }
    text.setAttribute('dominant-baseline','alphabetic');
    for(const span of text.querySelectorAll('tspan[y]'))span.setAttribute('y',String(Number(span.getAttribute('y'))-metrics.get(key)));
  }
}

let cjkReady = false;
async function prepareCJK(body) {
  if (!eastAsian.test(body.textContent)) return;
  if (!cjkReady) {
    const vfs = {};
    for (const name of ['CJK-Regular.otf', 'CJK-Bold.otf']) {
      const response = await fetch(`fonts/${name}`); if (!response.ok) throw new Error('Cannot load the bundled CJK font');
      const bytes = new Uint8Array(await response.arrayBuffer()); let binary = '';
      for (let n = 0; n < bytes.length; n += 16384) binary += String.fromCharCode.apply(null, bytes.subarray(n, n + 16384));
      vfs[name] = btoa(binary);
    }
    // addVirtualFileSystem replaces the active VFS; retain the text and code fonts too.
    Object.assign(window.ScriflowFonts,vfs);pdfMake.addVirtualFileSystem(window.ScriflowFonts); cjkReady = true;
  }
  const nodes = [], walker = document.createTreeWalker(body, NodeFilter.SHOW_TEXT);
  while (walker.nextNode()) if (eastAsian.test(walker.currentNode.textContent)) nodes.push(walker.currentNode);
  for (const text of nodes) {
    if (text.parentElement.namespaceURI === 'http://www.w3.org/2000/svg') {
      text.parentElement.setAttribute('font-family', 'ScriflowCJK'); continue;
    }
    const span = document.createElement('span'); span.style.fontFamily = 'ScriflowCJK';
    text.replaceWith(span); span.append(text);
  }
}

async function vectorAppearance(body) {
  await prepareCJK(body); await document.fonts.ready;
  for (const image of body.querySelectorAll('img')) if (!image.complete)
    await new Promise(resolve => { image.onload = image.onerror = resolve; });
  const bounds = body.getBoundingClientRect(), height = Math.max(40, Math.ceil(bounds.height));
  if (height > 16384 || body.scrollWidth > bounds.width + 4) throw new Error('This block is too large; split it into smaller Markdown blocks');
  const svg = elementToSVG(body).documentElement;
  normalizeBaselines(svg);
  svg.setAttribute('width', String(bounds.width)); svg.setAttribute('height', String(height));
  // Keep the DOM element: serializing through pdfmake's XML rewrite trims inline spaces.
  const pdf = await new Promise((resolve, reject) => {
    try { pdfMake.createPdf({ pageSize: { width: bounds.width * .75, height: height * .75 }, pageMargins: 0,
      compress: true, content: [{ svg, width: bounds.width * .75, height: height * .75 }] }).getBase64(resolve); }
    catch (error) { reject(error); }
  });
  if (pdf.length > Math.ceil(4 * 1024 * 1024 / 3) * 4) throw new Error('This Markdown block exceeds the vector size limit');
  return { width: bounds.width, height, pdf };
}

async function render(source, width) {
  if (result.phase === 'working') throw new Error('A Markdown render is already running');
  result = { phase: 'working' };
  try {
    if (typeof source !== 'string' || new TextEncoder().encode(source).length > 128 * 1024) throw new Error('Markdown source exceeds 128 KiB');
    if (!Number.isFinite(width) || width < 160 || width > 1600) throw new Error('Invalid Markdown layout width');
    equations = []; diagrams = [];
    const body = document.getElementById('block'); body.style.width = `${Math.round(width)}px`;
    const fragment = DOMPurify.sanitize(md.render(source), { RETURN_DOM_FRAGMENT:true,
      FORBID_TAGS: ['script', 'iframe', 'object', 'embed', 'form', 'style', 'link'],
      FORBID_ATTR: ['style', 'id', 'name','srcset'], ADD_ATTR: ['data-equation', 'data-diagram'], ADD_TAGS: ['eq', 'eqn', 'aside'] });
    normalize(fragment);
    while(body.firstChild)body.removeChild(body.firstChild);body.appendChild(fragment);
    await prepareCJK(body); await document.fonts.load('18px Roboto'); await document.fonts.ready;
    await MathJax.startup.promise; MathJax.typesetClear([body]); MathJax.texReset();
    for (const mark of body.querySelectorAll('[data-equation]')) {
      const eq = equations[Number(mark.dataset.equation)];
      if(!eq){mark.removeAttribute('data-equation');continue;}
      mark.textContent = eq.display ? `\\[${eq.source}\\]` : `\\(${eq.source}\\)`;
    }
    await MathJax.typesetPromise([body]);
    const errors = Array.from(body.querySelectorAll('[data-mjx-error]')).map(n => `Math: ${n.getAttribute('data-mjx-error')}`);
    for (const mark of body.querySelectorAll('[data-diagram]')) {
      try {
        const rendered = await mermaid.render(`diagram-${mark.dataset.diagram}`, diagrams[Number(mark.dataset.diagram)]);
        mark.innerHTML = DOMPurify.sanitize(rendered.svg, { USE_PROFILES: { svg: true, svgFilters: true } });
      } catch (error) {
        errors.push(`Diagram: ${String(error.message || error).slice(0,200)}`);
        const pre = document.createElement('pre'); pre.textContent = diagrams[Number(mark.dataset.diagram)]; mark.replaceWith(pre);
        document.getElementById(`ddiagram-${mark.dataset.diagram}`)?.remove();
      }
    }
    const rendered = await vectorAppearance(body);
    const code = document.createElement('pre'); code.className = 'md-source'; code.textContent = source || ' ';
    while(body.firstChild)body.removeChild(body.firstChild);body.appendChild(code);
    const original = await vectorAppearance(body);
    result = { phase: 'ready', rendered, original, warning: errors.join('\n').slice(0,1000) };
  } catch (error) { result = { phase: 'error', message: String(error && error.message || error).slice(0, 1000) }; }
}

window.ScriflowMarkdown = {
  render,
  info() { return { phase: result.phase, message: result.message, warning: result.warning,
    rw: result.rendered?.width, rh: result.rendered?.height, sw: result.original?.width, sh: result.original?.height,
    renderedLength: result.rendered?.pdf.length, sourceLength: result.original?.pdf.length }; },
  chunk(sourceMode, offset, count) { return (sourceMode ? result.original : result.rendered).pdf.slice(offset, offset + count); }
};
window.addEventListener('error', event => { if (result.phase === 'working') result = { phase: 'error', message: event.message }; });
window.addEventListener('unhandledrejection', event => { if (result.phase === 'working') result = { phase: 'error', message: String(event.reason?.message || event.reason).slice(0,1000) }; });
result = { phase: 'idle' };
