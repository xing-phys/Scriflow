/** Blob-compatible random access keeps the original book in native private storage. */
export class RemoteFile {
  constructor(size, name, read, start = 0, end = size) { Object.assign(this, { total: size, name, read, start, end, size: end - start, type: '' }); }
  slice(start = 0, end = this.size) {
    const clamp = value => Math.min(this.size, Math.max(0, value < 0 ? this.size + value : value));
    const a = clamp(start), b = Math.max(a, clamp(end));
    return new RemoteFile(this.total, this.name, this.read, this.start + a, this.start + b);
  }
  async arrayBuffer() {
    if (this.size > 64 * 1024 * 1024) throw Error('A single book resource exceeds the 64 MiB reading limit. Try an EPUB split into shorter chapters.');
    const result = new Uint8Array(this.size);
    for (let offset = 0; offset < result.length; offset += 262144) {
      const length = Math.min(262144, result.length - offset), bytes = await this.read(this.start + offset, length);
      if (bytes.length !== length) throw Error('The original book is truncated.'); result.set(bytes, offset);
    }
    return result.buffer;
  }
  async text() { return new TextDecoder().decode(await this.arrayBuffer()); }
}

export async function checkMobiEncryption(file) {
  const bytes = new Uint8Array(await file.slice(0, Math.min(86, file.size)).arrayBuffer());
  if (bytes.length < 86 || !['BOOKMOBI', 'TEXtREAd'].includes(new TextDecoder().decode(bytes.subarray(60, 68)))) return;
  const first = new DataView(bytes.buffer).getUint32(78);
  if (first > file.size - 16) throw Error('The MOBI record directory is truncated.');
  const record = new DataView(await file.slice(first, first + 16).arrayBuffer());
  if (record.getUint16(12) !== 0) throw Error('This book is DRM protected. Choose a DRM-free EPUB, MOBI or AZW3 copy.');
}

export function sanitizeDocument(doc) {
  for (const element of doc.querySelectorAll('script,iframe,object,embed,base,form,meta[http-equiv="refresh"]')) element.remove();
  for (const element of doc.querySelectorAll('*')) for (const attribute of [...element.attributes]) {
    if (/^on/i.test(attribute.name) || /^(srcdoc|formaction)$/i.test(attribute.name)
        || /^(href|src|action|xlink:href)$/i.test(attribute.name) && /^\s*(javascript|vbscript):/i.test(attribute.value)) element.removeAttributeNode(attribute);
  }
  const meta = doc.createElement('meta'); meta.setAttribute('http-equiv', 'Content-Security-Policy');
  meta.setAttribute('content', "default-src 'none'; script-src 'none'; connect-src 'none'; img-src blob: data:; style-src 'unsafe-inline' blob: data:; font-src blob: data:; media-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'");
  (doc.head ?? doc.documentElement).prepend(meta); return doc;
}

export function protectBook(book) {
  const urls = new Set(), destroy = book.destroy?.bind(book);
  for (const section of book.sections) {
    const load = section.load.bind(section), create = section.createDocument?.bind(section), unload = section.unload?.bind(section);
    let cached;
    section.load = async () => {
      if (cached) return cached;
      const source = await load();
      if (typeof source !== 'string' || !/^(blob:|data:)/.test(source)) throw Error('This book contains an unsupported external chapter.');
      const text = await (await fetch(source)).text();
      const doc = sanitizeDocument(new DOMParser().parseFromString(text, 'text/html'));
      cached = URL.createObjectURL(new Blob(['<!doctype html>' + doc.documentElement.outerHTML], { type: 'text/html' })); urls.add(cached); return cached;
    };
    if (create) section.createDocument = async () => sanitizeDocument(await create());
    section.unload = () => { if (cached) { URL.revokeObjectURL(cached); urls.delete(cached); cached = null; } unload?.(); };
  }
  book.destroy = () => { for (const url of urls) URL.revokeObjectURL(url); urls.clear(); destroy?.(); };
  return book;
}

export const escapeHTML = text => String(text).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

export async function textBook(file, html = false) {
  const buffer = await file.arrayBuffer(); let text;
  const bytes = new Uint8Array(buffer), bom = bytes[0] === 255 && bytes[1] === 254 ? 'utf-16le' : bytes[0] === 254 && bytes[1] === 255 ? 'utf-16be' : null;
  try { text = new TextDecoder(bom ?? 'utf-8', { fatal: true }).decode(buffer); }
  catch { text = new TextDecoder('gb18030').decode(buffer); }
  let documents;
  if (html) documents = [new DOMParser().parseFromString(text, 'text/html')];
  else {
    text = text.replace(/\r\n?/g, '\n'); const chunks = []; let start = 0;
    while (start < text.length) {
      let end = Math.min(start + 64000, text.length);
      if (end < text.length) { const boundary = text.lastIndexOf('\n', end); if (boundary > start + 32000) end = boundary + 1; }
      chunks.push(text.slice(start, end)); start = end;
    }
    documents = chunks.map(chunk => new DOMParser().parseFromString('<!doctype html><html><head><meta charset="utf-8"></head><body>'
      + chunk.split(/\n\s*\n/).map(p => '<p>' + escapeHTML(p).replace(/\n/g, '<br>') + '</p>').join('') + '</body></html>', 'text/html'));
  }
  if (!documents.length) throw Error('This text book is empty.');
  const urls = new Map(), sections = documents.map((doc, index) => ({
    id: String(index), size: doc.documentElement.textContent.length,
    createDocument: async () => doc.cloneNode(true),
    load: async () => {
      if (!urls.has(index)) urls.set(index, URL.createObjectURL(new Blob(['<!doctype html>' + doc.documentElement.outerHTML], { type: 'text/html' })));
      return urls.get(index);
    }, unload: () => { URL.revokeObjectURL(urls.get(index)); urls.delete(index); },
  }));
  return { sections, metadata: { title: file.name.replace(/\.[^.]+$/, '') }, toc: sections.map((s,i) => ({ label: html ? docTitle(documents[i]) : 'Section ' + (i+1), href: String(i) })),
    resolveHref: href => ({ index: Number(href) }), splitTOCHref: href => [String(href), null], getTOCFragment: doc => doc.body,
    destroy: () => { for (const url of urls.values()) URL.revokeObjectURL(url); } };
}
function docTitle(doc) { return doc.title || doc.querySelector('h1')?.textContent || 'Document'; }
