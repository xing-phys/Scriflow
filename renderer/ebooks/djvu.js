import init, { WasmDocument, WasmLazyDocument } from './vendor/djvu/djvu_rs.js';
import { escapeHTML } from './io.js';

/** Fixed-page adapter. Large bundled DjVu books stay on random-access page reads. */
export async function djvuBook(file) {
  await init();
  const direct = file.size <= 32 * 1024 * 1024 ? WasmDocument.from_bytes(new Uint8Array(await file.arrayBuffer())) : null;
  const lazy = direct ? null : await WasmLazyDocument.open(file.size, async (offset, length) => new Uint8Array(await file.slice(offset, offset + length).arrayBuffer()));
  const count = (direct ?? lazy).page_count();
  if (count < 1 || count > 20000) throw Error('DjVu reading supports 1–20,000 pages.');
  const urls = new Map();
  async function info(index) {
    if (lazy) return Array.from(await lazy.page_info(index));
    const page = direct.page(index);
    try { return [page.width_at(page.dpi()), page.height_at(page.dpi()), page.dpi()]; } finally { page.free(); }
  }
  async function document(index) {
    const [w,h,dpi] = await info(index);
    if (w < 1 || h < 1 || w > 100000 || h > 100000 || dpi < 1) throw Error('Invalid DjVu page geometry.');
    let zones = [];
    if (direct) { const page = direct.page(index); try { zones = JSON.parse(page.text_zones_json(dpi) ?? '[]'); } finally { page.free(); } }
    const spans = zones.map(z => '<span style="position:absolute;white-space:pre;left:' + Number(z.x) + 'px;top:' + Number(z.y) + 'px;font-size:' + Math.max(1,Number(z.h)) + 'px;line-height:1">' + escapeHTML(z.t) + '</span>').join('');
    return { w,h,dpi, doc: new DOMParser().parseFromString('<!doctype html><html><head><meta name="viewport" content="width=' + w + ',height=' + h + '">'
      + '<meta http-equiv="Content-Security-Policy" content="default-src \'none\'; script-src \'none\'; style-src \'unsafe-inline\'; img-src blob: data:"></head>'
      + '<body style="margin:0;overflow:hidden"><canvas style="position:absolute;width:100vw;height:100vh"></canvas><div id="text" style="position:absolute;transform-origin:top left;color:transparent;width:' + w + 'px;height:' + h + 'px">' + spans + '</div></body></html>', 'text/html') };
  }
  const sections = Array.from({ length: count }, (_, index) => ({
    id: String(index), size: 1500,
    createDocument: async () => (await document(index)).doc,
    load: async () => {
      const {w,h,dpi,doc} = await document(index);
      if (urls.has(index)) URL.revokeObjectURL(urls.get(index));
      const src = URL.createObjectURL(new Blob(['<!doctype html>' + doc.documentElement.outerHTML], { type: 'text/html' })); urls.set(index, src);
      let generation = 0, last = 0;
      return { src, onZoom: async ({ doc, scale }) => {
        doc.querySelector('#text').style.transform = `scale(${scale})`;
        const pixels = Math.min(3, scale * devicePixelRatio, 4096/w, 4096/h, Math.sqrt(8*1024*1024/(w*h)));
        if (Math.abs(pixels - last) < .02) return;
        last = pixels; const request = ++generation;
        try {
          let bytes, width, height;
          if (direct) { const page = direct.page(index); try { const target = Math.max(1,Math.round(dpi*pixels)); bytes=page.render(target); width=page.width_at(target); height=page.height_at(target); } finally { page.free(); } }
          else { const pixmap = await lazy.render_page(index,Math.max(1,Math.round(dpi*pixels))); try { bytes=pixmap.to_bytes(); width=pixmap.width(); height=pixmap.height(); } finally { pixmap.free(); } }
          if (request !== generation) return;
          const canvas = doc.querySelector('canvas'); canvas.width=width; canvas.height=height; canvas.getContext('2d').putImageData(new ImageData(bytes,width,height),0,0);
        } catch (error) { doc.body.append(Object.assign(doc.createElement('p'), {textContent:'Could not render this DjVu page: '+error.message})); }
      } };
    },
    unload: () => { if (urls.has(index)) URL.revokeObjectURL(urls.get(index)); urls.delete(index); },
  }));
  return { sections, metadata: { title: file.name.replace(/\.djvu?$/i,'') }, rendition: { layout:'pre-paginated', spread:'none' },
    toc: sections.map((s,i)=>({label:'Page '+(i+1),href:String(i)})), resolveHref:href=>({index:Number(href)}), splitTOCHref:href=>[String(href),null], getTOCFragment:doc=>doc.body,
    textUnavailable:!direct, destroy:()=>{for(const url of urls.values())URL.revokeObjectURL(url);direct?.free();lazy?.free();} };
}
