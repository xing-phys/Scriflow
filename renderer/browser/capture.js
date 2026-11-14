import postcss from 'postcss';
import valueParser from 'postcss-value-parser';

const CSP = "default-src 'none'; img-src data:; style-src 'unsafe-inline'; font-src data:; frame-src data: about:; media-src data:; script-src 'none'; connect-src 'none'; object-src 'none'; form-action 'none'; base-uri 'none'";
const withoutHash = url => url.split('#')[0];

/** Capture the rendered DOM, not the original response. All fetches are supplied by the owner. */
export async function captureDocument(document, fetchResource, depth = 0) {
  if (depth > 8) throw Error('Too many nested frames');
  const sourceUrl = document.location?.href || document.URL;
  const snapshot = document.implementation.createHTMLDocument('');
  const root = snapshot.importNode(document.documentElement, true);
  snapshot.replaceChild(root, snapshot.documentElement);
  const original = [document.documentElement, ...document.documentElement.querySelectorAll('*')];
  const copied = [root, ...root.querySelectorAll('*')];
  let missing = 0;
  const resources = new Map();
  async function resource(value, base) {
    const resolved = new URL(value, base);
    if (resolved.protocol === 'data:') return resolved.href;
    const fragment = resolved.hash; resolved.hash = '';
    if (!resources.has(resolved.href)) resources.set(resolved.href, (async () => {
      if (resolved.protocol === 'blob:') {
        const blob = await (await fetch(resolved.href)).blob();
        return await new Promise((resolve, reject) => {
          const reader = new FileReader(); reader.onload = () => resolve(reader.result); reader.onerror = reject; reader.readAsDataURL(blob);
        });
      }
      if (resolved.protocol !== 'https:' && resolved.protocol !== 'http:') throw Error('Unsupported capture resource');
      const fetched = await fetchResource(resolved.href);
      return 'data:' + fetched.mime + ';base64,' + fetched.data;
    })());
    return await resources.get(resolved.href) + fragment;
  }
  async function cssValue(value, base) {
    const tree = valueParser(value), work = [];
    tree.walk(node => {
      if (node.type !== 'function' || node.value.toLowerCase() !== 'url') return;
      const raw = node.nodes.length === 1 && node.nodes[0].type === 'string' ? node.nodes[0].value : valueParser.stringify(node.nodes).trim();
      if (!raw || raw.startsWith('#')) return;
      work.push(resource(raw, base).then(data => { node.nodes = [{ type: 'string', quote: '"', value: data }]; }).catch(() => {
        missing++; node.nodes = [{ type: 'string', quote: '"', value: 'data:,' }];
      }));
    });
    await Promise.all(work); return tree.toString();
  }
  async function css(text, base, seen = new Set()) {
    const tree = postcss.parse(text), work = [];
    tree.walkAtRules('import', rule => {
      work.push((async () => {
        const parsed = valueParser(rule.params), first = parsed.nodes.find(node => node.type !== 'space' && node.type !== 'comment');
        let href;
        if (first?.type === 'string') href = first.value;
        else if (first?.type === 'function' && first.value.toLowerCase() === 'url') href = first.nodes[0]?.value;
        if (!href) { rule.remove(); return; }
        const url = new URL(href, base).href;
        if (seen.has(url)) { rule.remove(); return; }
        const next = new Set(seen); next.add(url);
        try {
          const fetched = await fetchResource(url);
          const bytes = Uint8Array.from(atob(fetched.data), char => char.charCodeAt(0));
          const imported = postcss.parse(await css(new TextDecoder().decode(bytes), fetched.url || url, next));
          const tail = parsed.nodes.slice(parsed.nodes.indexOf(first) + 1).filter(node => node.type !== 'comment');
          const significant = () => { while (tail[0]?.type === 'space') tail.shift(); return tail[0]; };
          let layer, supports;
          if (significant()?.value === 'layer') {
            const token = tail.shift(); layer = token.type === 'function' ? valueParser.stringify(token.nodes) : '';
          }
          if (significant()?.type === 'function' && tail[0].value === 'supports') supports = valueParser.stringify(tail.shift().nodes);
          let nodes = imported.nodes;
          const wrap = (name, params) => { const wrapper = postcss.atRule({ name, params }); wrapper.append(nodes); nodes = [wrapper]; };
          const media = valueParser.stringify(tail).trim();
          if (media && media !== 'all') wrap('media', media);
          if (supports != null) wrap('supports', '(' + supports + ')');
          if (layer != null) wrap('layer', layer);
          rule.replaceWith(...nodes);
        } catch { missing++; rule.remove(); }
      })());
    });
    await Promise.all(work);
    work.length = 0;
    tree.walkDecls(declaration => work.push(cssValue(declaration.value, base).then(value => { declaration.value = value; })));
    await Promise.all(work); return tree.toString();
  }
  const work = [];
  for (let i = 0; i < original.length; i++) {
    const node = original[i], copy = copied[i], tag = node.localName?.toLowerCase();
    if (['script', 'noscript', 'base', 'object', 'embed', 'source'].includes(tag)
        || tag === 'meta' && node.hasAttribute('http-equiv')) { copy.remove(); continue; }
    for (const attribute of [...copy.attributes]) {
      if (/^on/i.test(attribute.name) || ['integrity', 'nonce', 'ping', 'srcset', 'sizes', 'loading', 'autofocus'].includes(attribute.name)) copy.removeAttribute(attribute.name);
    }
    if (copy.hasAttribute('style')) work.push(cssValue(copy.getAttribute('style'), node.baseURI).then(value => copy.setAttribute('style', value)));
    if (tag === 'link') {
      if (node.relList.contains('stylesheet') && !node.disabled) work.push((async () => {
        try {
          const fetched = await fetchResource(node.href), text = new TextDecoder().decode(Uint8Array.from(atob(fetched.data), char => char.charCodeAt(0)));
          const style = snapshot.createElement('style'); style.textContent = await css(text, fetched.url || node.href);
          if (node.media) style.media = node.media; copy.replaceWith(style);
        } catch { missing++; copy.remove(); }
      })()); else copy.remove();
    } else if (tag === 'style') {
      let text = node.textContent;
      if (!text.trim()) { try { text = [...node.sheet.cssRules].map(rule => rule.cssText).join('\n'); } catch { } }
      work.push(css(text, node.baseURI).then(value => { copy.textContent = value; }));
    } else if (tag === 'img' || tag === 'image') {
      const value = tag === 'img' ? node.currentSrc || node.src : node.getAttribute('href') || node.getAttribute('xlink:href');
      if (value) work.push(resource(value, node.baseURI).then(data => {
        copy.setAttribute(tag === 'img' ? 'src' : 'href', data); copy.removeAttribute('xlink:href');
      }).catch(() => { missing++; copy.removeAttribute('src'); copy.removeAttribute('href'); copy.removeAttribute('xlink:href'); }));
    } else if (tag === 'iframe') {
      copy.removeAttribute('src'); copy.setAttribute('sandbox', '');
      try {
        const child = node.contentDocument;
        if (child?.documentElement) work.push(captureDocument(child, fetchResource, depth + 1).then(result => { copy.srcdoc = result.html; missing += result.missing; }));
        else { missing++; copy.srcdoc = '<!doctype html><p>This embedded page needs the online original.</p>'; }
      } catch { missing++; copy.srcdoc = '<!doctype html><p>This embedded page needs the online original.</p>'; }
    } else if (tag === 'canvas') {
      try {
        const image = snapshot.createElement('img'); image.src = node.toDataURL(); image.style.cssText = copy.style.cssText;
        image.style.width = node.getBoundingClientRect().width + 'px'; image.style.maxWidth = '100%'; image.alt = 'Saved canvas'; copy.replaceWith(image);
      } catch { missing++; copy.replaceWith(snapshot.createTextNode('[Canvas available in online original]')); }
    } else if (['video', 'audio', 'track'].includes(tag)) {
      copy.removeAttribute('src'); copy.removeAttribute('autoplay');
    } else if (tag === 'input') {
      if (node.type === 'password' || node.type === 'file') { copy.removeAttribute('value'); copy.value = ''; }
      else { copy.setAttribute('value', node.value); if (node.checked) copy.setAttribute('checked', ''); else copy.removeAttribute('checked'); }
    } else if (tag === 'textarea') copy.textContent = node.value;
    if (tag === 'a' || tag === 'area') {
      const href = node.getAttribute('href');
      if (href) {
        try {
          const url = new URL(href, node.baseURI);
          if (withoutHash(url.href) === withoutHash(sourceUrl) && url.hash) copy.setAttribute('href', url.hash);
          else if (url.protocol === 'https:') copy.setAttribute('href', url.href);
          else copy.removeAttribute('href');
        } catch { copy.removeAttribute('href'); }
      }
      copy.removeAttribute('target'); copy.removeAttribute('download');
    }
  }
  await Promise.all(work);
  const head = snapshot.head || root.insertBefore(snapshot.createElement('head'), root.firstChild);
  const security = snapshot.createElement('meta'); security.setAttribute('http-equiv', 'Content-Security-Policy'); security.content = CSP; head.prepend(security);
  const charset = snapshot.createElement('meta'); charset.setAttribute('charset', 'utf-8'); head.prepend(charset);
  const html = '<!DOCTYPE html>\n<!-- Saved by Scriflow -->\n' + root.outerHTML;
  if (html.length > 64 * 1024 * 1024) throw Error('Page exceeds capture size limit');
  return { html, missing, url: sourceUrl, title: document.title };
}

export function pageStats(document) {
  return { url: document.location.href, title: document.title, ready: document.readyState,
    userAgent: document.defaultView.navigator.userAgent, scrollY: document.defaultView.scrollY,
    width: document.defaultView.innerWidth, documentWidth: document.documentElement.scrollWidth,
    math: document.querySelectorAll('.math').length, renderedMath: document.querySelectorAll('mjx-container, .katex').length,
    brokenImages: [...document.images].filter(image => image.complete && image.naturalWidth === 0).length };
}
