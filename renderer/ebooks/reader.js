import { makeBook } from './vendor/foliate/view.js';
import { Overlayer } from './vendor/foliate/overlayer.js';
import { RemoteFile, protectBook, textBook, checkMobiEncryption } from './io.js';

const $ = id => document.getElementById(id), port = browser.runtime.connectNative('scriflowEbooks');
const pending = new Map(); let sequence = 0, view, config, book, state, selected, searchGeneration = 0, customFont, disconnected=false;
const defaults = { font:'publisher', size:20, weight:400, line:1.5, margin:20, align:'start', columns:1, flow:'paginated', zoom:'fit-page' };
const send = (type, fields = {}) => { if(!disconnected)port.postMessage({ type, ...fields }); };
port.onDisconnect?.addListener(()=>{disconnected=true;for(const request of pending.values()){clearTimeout(request.timer);request.reject(Error('The reader was closed.'));}pending.clear();});
const notice = message => { $('notice').textContent = String(message ?? ''); };
const attempt = work => Promise.resolve().then(work).catch(error => { notice(error.message || String(error)); send('error', { message:error.message || String(error), stack:config?.debug ? error.stack : undefined }); });
function read(offset, length, channel = 'book') {
  return new Promise((resolve,reject) => {
    const id = ++sequence, timer = setTimeout(()=>{pending.delete(id);reject(Error('The book read timed out. Close and reopen it to retry.'));},60000);
    pending.set(id,{resolve,reject,timer}); send('read',{id,offset,length,channel});
  });
}
function flush() { if (state) send('state',{state}); }
// Native IO coalesces writes. Send changes immediately so a quick Activity close
// can save the latest state without waiting for a JavaScript debounce timer.
function changed() { state.modified=Math.max(Date.now(),(state.modified||0)+1); flush(); }
function normalize(input) {
  const value = input && typeof input === 'object' ? input : {};
  return {version:1, ...value, settings:{...defaults,...value.settings}, annotations:Array.isArray(value.annotations)?value.annotations:[], location:typeof value.location==='string'?value.location:'', fraction:Number(value.fraction)||0};
}
function text(value) {
  if (typeof value === 'string') return value;
  if (Array.isArray(value)) return value.map(text).join(', ');
  if (value?.name) return text(value.name);
  return value && typeof value==='object' ? text(Object.values(value)[0]) : '';
}
function panel(title) { $('panel-title').textContent=title; $('panel-content').replaceChildren(); if (!$('panel').open) $('panel').showModal(); return $('panel-content'); }
function closePanel() { $('panel').close(); }
function button(parent,label,action) {
  const node=document.createElement('button');node.textContent=label;node.onclick=()=>attempt(action);parent.append(node);return node;
}
function para(parent,value,className='') { const node=document.createElement('p');node.textContent=value;node.className=className;parent.append(node);return node; }
function annotations() { return state?.annotations ?? []; }
function at(value) { return annotations().find(a=>a.value===value); }
function target() {
  if (selected) return selected;
  const current=view?.lastLocation;
  return {value:current?.cfi || state.location, text:current?.tocItem?.label || text(book?.metadata?.title) || config.name};
}
function ensureAnnotation(target,add=true) {
  if (!target?.value) throw Error('Wait for the book to finish opening.');
  let item=at(target.value);
  if (!item) {
    if (annotations().length>=2000) throw Error('This book has 2,000 annotations. Export notes and remove older entries before adding more.');
    item={id:crypto.randomUUID(),value:target.value,text:String(target.text||'').slice(0,4000),mark:!!target.mark,created:Date.now()};if(add)state.annotations.push(item);
  }
  return item;
}
async function draw(item, remove=false) {
  if (!view || !item?.value) return;
  const visible=a=>!a.snapshot&&(a.highlight||a.mark&&(a.note||a.notebook));
  if(!remove&&!visible(item))return;
  if(remove){const other=annotations().find(a=>a!==item&&a.value===item.value&&visible(a));if(other)return draw(other);}
  try { await view.addAnnotation({value:item.value,color:item.highlight?'#cccccc':'#777777',...item},remove); }
  catch { /* The original text anchor remains in Notes even if a renderer cannot draw it. */ }
}
async function restyle() {
  if (!view) return;
  const s=state.settings, size=Math.min(48,Math.max(12,Number(s.size)||20)); s.size=size;
  if (view.isFixedLayout) { view.renderer.setAttribute('zoom',s.zoom || 'fit-page'); return; }
  view.renderer.setAttribute('flow',s.flow==='scrolled'?'scrolled':'paginated');
  const margin=Math.min(80,Math.max(0,Number(s.margin)||0));
  view.renderer.setAttribute('margin',margin+'px');
  view.renderer.setAttribute('gap',Math.min(30,200*margin/Math.max(320,$('reader').clientWidth))+'%');
  view.renderer.setAttribute('max-inline-size',s.columns===2?'300px':'900px');
  view.renderer.setAttribute('max-column-count',s.columns===2?'2':'1');
  view.renderer.setAttribute('max-column-count-portrait',s.columns===2?'2':'1');
  let family='';
  if (s.font==='custom' && config.fontSize>0) { await loadFont(); family='"ScriflowBookFont"'; }
  else if (s.font!=='publisher') family={serif:'serif',sans:'sans-serif',mono:'monospace'}[s.font]||'serif';
  view.renderer.setStyles(`html{color:#000!important;background:#fff!important;font-size:${size}px!important;color-scheme:light}body{color:#000!important;background:#fff!important;font-size:1rem!important;line-height:${s.line}!important}p,li,blockquote,dd,dt{font-size:1rem!important;line-height:${s.line}!important;text-align:${s.align==='justify'?'justify':'start'}!important;font-weight:${s.weight}!important}p span,li span,blockquote span{font-size:inherit!important}${family?'body,p,li,blockquote,dd,dt,h1,h2,h3,h4,h5,h6,span:not(math *){font-family:'+family+'!important}':''}img,svg,video{max-width:100%!important}pre{white-space:pre-wrap!important}a{color:#000}::selection{color:#000;background:#bbb}`);
  for (const item of annotations()) await draw(item);
}
async function loadFont(force=false) {
  if (customFont && !force) return;
  if (customFont) document.fonts.delete(customFont);
  const file=new RemoteFile(config.fontSize,'font.ttf',(offset,length)=>read(offset,length,'font'));
  customFont=await new FontFace('ScriflowBookFont',await file.arrayBuffer()).load(); document.fonts.add(customFont);
  // Book sections have separate document font sets. FontFace objects can be added to each.
  for (const {doc} of view?.renderer.getContents()??[]) doc.fonts.add(customFont);
}
function rangeTarget(doc,index) {
  const selection=doc.defaultView.getSelection();
  if (!selection?.rangeCount || selection.isCollapsed || !selection.toString().trim()) return null;
  return {value:view.getCFI(index,selection.getRangeAt(0)),text:selection.toString().trim().slice(0,4000),mark:true};
}
function selectionChanged(doc,index) {
  const value=rangeTarget(doc,index);if(!value)return;
  selected=value;$('selection').hidden=false;
}
function clearSelection() { selected=null;$('selection').hidden=true;view?.deselect(); }
function loaded({detail:{doc,index}}) {
  if(customFont)doc.fonts.add(customFont);
  doc.addEventListener('selectionchange',()=>selectionChanged(doc,index));
  doc.addEventListener('pointerup',()=>setTimeout(()=>selectionChanged(doc,index),60));
  // Reflowable books already have paginator touch handling. Fixed pages need
  // a page-turn swipe only at fit-page zoom, so zoomed-page panning stays free.
  if(view.isFixedLayout){let down;
    doc.addEventListener('pointerdown',e=>{if(e.pointerType==='touch')down={x:e.clientX,y:e.clientY,t:Date.now()};});
    doc.addEventListener('pointerup',e=>{
      if(!down)return;const d=down;down=null;
      if(state.settings.zoom!=='fit-page'||Date.now()-d.t>650||rangeTarget(doc,index)||e.target.closest('a,input,button'))return;
      const dx=e.clientX-d.x,dy=e.clientY-d.y;if(Math.abs(dx)>90&&Math.abs(dx)>Math.abs(dy)*2)attempt(()=>dx<0?view.goRight():view.goLeft());
    });
  }
  doc.addEventListener('keydown',e=>{if(e.key==='ArrowRight')attempt(()=>view.goRight());if(e.key==='ArrowLeft')attempt(()=>view.goLeft());});
  for(const item of annotations())draw(item);
}
async function open(message) {
  config=message;state=normalize(message.state);
  notice('Opening '+message.name+'…');
  const file=new RemoteFile(message.size,message.name,(offset,length)=>read(offset,length));
  if(message.format==='mobi'||message.format==='azw3')await checkMobiEncryption(file);
  if(message.format==='djvu'){const {djvuBook}=await import('./djvu.js');book=await djvuBook(file);}
  else book=protectBook(message.format==='txt'||message.format==='html'?await textBook(file,message.format==='html'):await makeBook(file));
  view=document.createElement('foliate-view');$('reader').replaceChildren(view);
  view.addEventListener('load',loaded);
  view.addEventListener('relocate',({detail})=>{
    if(!detail.cfi)return;state.location=detail.cfi;state.fraction=Number(detail.fraction)||0;changed();
    const percentage=Math.round(state.fraction*100),label=detail.tocItem?.label||'';
    $('position').textContent=percentage+'%'+(label?' · '+label:'');
    $('bookmark').setAttribute('aria-pressed',String(annotations().some(a=>a.bookmark&&a.value===detail.cfi)));
  });
  view.addEventListener('external-link',e=>{e.preventDefault();send('external-link',{url:e.detail.href_});});
  view.addEventListener('create-overlay',()=>{for(const item of annotations())draw(item);});
  view.addEventListener('draw-annotation',({detail:{draw,annotation}})=>draw(annotation.highlight?Overlayer.highlight:Overlayer.underline,{color:annotation.highlight?'#cccccc':'#777777'}));
  view.addEventListener('show-annotation',({detail})=>{const item=at(detail.value);if(item)showAnnotation(item);});
  await view.open(book);await restyle();await view.init({lastLocation:state.location,showTextStart:true});
  notice('');send('ready',{title:text(book.metadata?.title)||config.name,author:text(book.metadata?.author),format:config.format,sections:book.sections.length});
}

function typography() {
  if(!view)return;
  const body=panel(view.isFixedLayout?'Page zoom':'Text and layout'),s=state.settings;
  function choice(label,key,values) {
    const row=document.createElement('label'),name=document.createElement('span');name.textContent=label;row.append(name);
    const select=document.createElement('select');select.setAttribute('aria-label',label);
    for(const [title,value] of values){const option=new Option(title,String(value));select.add(option);}select.value=String(s[key]);
    select.onchange=()=>attempt(async()=>{s[key]=typeof s[key]==='number'?Number(select.value):select.value;await restyle();changed();});row.append(select);body.append(row);return select;
  }
  if(view.isFixedLayout){choice('Zoom','zoom',[['Fit page','fit-page'],['Fit width','fit-width'],['100%','1'],['150%','1.5'],['200%','2']]);return;}
  choice('Font','font',[['Book font','publisher'],['Serif','serif'],['Sans serif','sans'],['Monospace','mono'],...(config.fontSize>0?[['Imported font','custom']]:[])]);
  button(body,'Import TTF / OTF font',()=>send('import-font'));
  choice('Size','size',Array.from({length:19},(_,i)=>[String(12+i*2)+' px',12+i*2]));
  choice('Weight','weight',[['Normal',400],['Medium',500],['Semibold',600],['Bold',700]]);
  choice('Line spacing','line',[[1,1],[1.2,1.2],[1.4,1.4],[1.5,1.5],[1.6,1.6],[1.8,1.8],[2,2]]);
  choice('Margins','margin',[[0,0],[10,10],[20,20],[30,30],[40,40],[60,60]]);
  choice('Alignment','align',[['Start','start'],['Justified','justify']]);
  choice('Columns','columns',[['One',1],['Two',2]]);
  choice('Reading','flow',[['Page turns','paginated'],['Continuous scroll','scrolled']]);
  para(body,'Settings apply to this book. Bookmarks, highlights and handwritten-note links follow the text.','muted');
  button(body,'Reset text settings',async()=>{state.settings={...defaults};await restyle();changed();typography();});
}
function contents() {
  if(!view)return;const body=panel('Contents');
  function add(entries,depth=0){for(const entry of entries??[]){const node=button(body,entry.label||'Section',async()=>{closePanel();clearSelection();await view.goTo(entry.href);});node.style.paddingInlineStart=(12+depth*16)+'px';add(entry.subitems,depth+1);}}
  add(book.toc);if(!body.children.length)para(body,'This book has no table of contents. Use the reading progress control to jump.');
}
async function find() {
  if(!view)return;const generation=++searchGeneration,body=panel('Search book');
  if(book.textUnavailable){para(body,'This large DjVu book is loaded page by page. Full-text search is unavailable in this mode.');return;}
  const input=document.createElement('input');input.type='search';input.placeholder='Search text';input.setAttribute('aria-label','Search book text');input.style.width='100%';body.append(input);
  const result=document.createElement('div');body.append(result);
  async function run(){
    const query=input.value.trim();if(!query)return;const owner=++searchGeneration;result.replaceChildren();para(result,'Searching…');let total=0;
    for await(const group of view.search({query,matchCase:false})){
      if(owner!==searchGeneration||!$('panel').open)return;
      const items=group?.subitems??(group?.cfi?[group]:[]);
      for(const item of items){if(total++===0)result.replaceChildren();const excerpt=item.excerpt;
        const label=typeof excerpt==='string'?excerpt:[excerpt?.pre,excerpt?.match,excerpt?.post].filter(Boolean).join('');
        button(result,(group.label?group.label+' · ':'')+(label||query),async()=>{closePanel();clearSelection();await view.goTo(item.cfi);});
        if(total>=200){para(result,'200 results shown. Narrow the search for more.','muted');return;}}
    }
    if(!total){result.replaceChildren();para(result,'No matches. Image-only pages need a text layer for search.');}
  }
  button(body,'Find',run);input.onkeydown=e=>{if(e.key==='Enter')attempt(run);};input.focus();
}
function listNotes() {
  if(!view)return;const body=panel('Bookmarks and notes');
  if(!annotations().length){para(body,'Bookmark the current position, or select text to highlight it and attach a note. Handwritten notes open in the native Scriflow editor.');return;}
  for(const item of annotations()){
    const row=document.createElement('article');row.className='annotation';body.append(row);
    para(row,(item.bookmark?'Bookmark · ':'')+(item.snapshot?'Fixed copy · ':item.notebook?'Handwriting · ':'')+(item.text||'Reading position'));
    if(item.note)para(row,item.note);const actions=document.createElement('div');row.append(actions);
    button(actions,'Go to text',async()=>{closePanel();clearSelection();await view.goTo(item.value);});
    if(item.notebook)button(actions,'Open handwriting',()=>{flush();send('open-note',{notebook:item.notebook});});
    button(actions,'Edit note',()=>editNote(item));
    button(actions,'Remove link / highlight',()=>confirmRemove(item));
  }
}
function showAnnotation(item){const body=panel('Annotation');para(body,item.text||'Reading position');if(item.note)para(body,item.note);
  if(item.notebook)button(body,'Open handwriting',()=>{closePanel();flush();send('open-note',{notebook:item.notebook});});
  button(body,'Edit note',()=>editNote(item));button(body,'All notes',listNotes);
}
function confirmRemove(item){const body=panel('Remove annotation?');para(body,'Remove this bookmark, highlight and note link? Linked handwritten notebooks remain on the bookshelf.');
  button(body,'Remove',async()=>{state.annotations=annotations().filter(a=>a!==item);await draw(item,true);changed();listNotes();});button(body,'Cancel',listNotes);
}
function editNote(item) {
  const body=panel('Text note');para(body,item.text||'Reading position');const input=document.createElement('textarea');input.value=item.note||'';input.maxLength=16000;input.setAttribute('aria-label','Note text');body.append(input);
  button(body,'Save note',async()=>{
    item.note=input.value.trim();
    if(item.note&&!annotations().includes(item))state.annotations.push(item);
    if(!item.note&&!item.highlight&&!item.bookmark&&!item.notebook)state.annotations=annotations().filter(a=>a!==item);
    changed();await draw(item,!item.note&&!item.highlight&&!item.notebook);closePanel();clearSelection();
  });
}
function bookmark(){if(!view)return;const item=ensureAnnotation(target());item.bookmark=!item.bookmark;if(!item.bookmark&&!item.highlight&&!item.note&&!item.notebook)state.annotations=annotations().filter(a=>a!==item);changed();$('bookmark').setAttribute('aria-pressed',String(item.bookmark));notice(item.bookmark?'Bookmarked.':'Bookmark removed.');}
function handwrite(){if(!view)return;const current=target();clearSelection();flush();send('handwrite',{...current,state});}
function fixedCopy(){
  if(!view)return;const body=panel('Write on a fixed copy');
  para(body,'Create a PDF copy of the current visible reading view. You can write directly on it with Scriflow’s pen tools. This copy keeps today’s text layout; the ebook remains adjustable.');
  button(body,'Create copy and annotate',captureSnapshot);button(body,'Cancel',closePanel);
}
async function captureSnapshot(){
  if($('panel').open)closePanel();clearSelection();flush();const current=target();
  document.body.classList.add('snapshot');await new Promise(requestAnimationFrame);await new Promise(requestAnimationFrame);
  const box=$('reader').getBoundingClientRect();send('snapshot',{...current,state,box:{x:box.x,y:box.y,width:box.width,height:box.height,viewportWidth:innerWidth}});
}
function exports() {
  const body=panel('Export reading notes');para(body,'JSON includes reading settings, text anchors, bookmarks and text notes. Linked handwriting is stored in ordinary Scriflow notebooks; export those separately from the bookshelf.');
  button(body,'Save JSON annotations',()=>{flush();send('export',{name:'reading-notes.json',mime:'application/json',text:JSON.stringify({book:config.id,title:text(book.metadata?.title),...state},null,2)});});
  button(body,'Save Markdown notes',()=>{
    const title=text(book.metadata?.title)||config.name,lines=['# '+title,''];
    for(const item of annotations()){lines.push('> '+String(item.text||'Reading position').replace(/\n/g,'\n> '),'',item.note||'',item.notebook?'Handwritten notebook: '+item.notebook:'','Location: `'+item.value+'`','');}
    send('export',{name:'reading-notes.md',mime:'text/markdown',text:lines.join('\n')});
  });
}
function more(){if(!view)return;const body=panel('Reading tools');
  button(body,'Handwritten note at this position',()=>{closePanel();handwrite();});
  button(body,'Write on a fixed copy of this view',fixedCopy);
  button(body,'Export reading notes',exports);
  button(body,'Focus reading / show controls',()=>{closePanel();document.body.classList.toggle('zen');send('zen',{enabled:document.body.classList.contains('zen')});});
  para(body,'Open the toolbox with Android Back while focus reading is active. Reading positions and annotations are stored with this book.','muted');
}
function jump(){if(!view)return;const body=panel('Reading position'),input=document.createElement('input');input.type='range';input.min='0';input.max='100';input.value=String(Math.round(state.fraction*100));input.setAttribute('aria-label','Book progress');input.style.width='100%';body.append(input);
  const value=para(body,input.value+'%');input.oninput=()=>value.textContent=input.value+'%';
  button(body,'Go',async()=>{closePanel();clearSelection();await view.goToFraction(Number(input.value)/100);});
  para(body,'The percentage follows the text. Screen page counts can change when font size, spacing or orientation changes.','muted');
}

for(const [id,handler] of Object.entries({type:typography,contents,search:find,bookmark,notes:listNotes,more,position:jump,
  previous:()=>{clearSelection();return view?.prev();},next:()=>{clearSelection();return view?.next();},
  'history-back':()=>view?.history.back(),'history-forward':()=>view?.history.forward(),
  highlight:async()=>{const item=ensureAnnotation(target());item.highlight=true;changed();await draw(item);clearSelection();},
  'text-note':()=>editNote(ensureAnnotation(target(),false)),handwrite,
  copy:()=>{if(selected)send('copy',{text:selected.text});clearSelection();},lookup:()=>{if(selected)send('lookup',{text:selected.text});},'clear-selection':clearSelection,
  'close-panel':closePanel}))$(id).onclick=()=>attempt(handler);
$('panel').addEventListener('close',()=>{searchGeneration++;});
addEventListener('pagehide',flush);addEventListener('visibilitychange',()=>{if(document.hidden)flush();});
port.onMessage.addListener(message=>attempt(async()=>{
  if(message.type==='read-result'){
    const request=pending.get(message.id);if(!request)return;pending.delete(message.id);clearTimeout(request.timer);
    if(message.error){request.reject(Error(message.error));return;}
    const binary=atob(message.data),bytes=new Uint8Array(binary.length);for(let i=0;i<bytes.length;i++)bytes[i]=binary.charCodeAt(i);request.resolve(bytes);
  }else if(message.type==='open')await open(message);
  else if(message.type==='flush')flush();
  else if(message.type==='close'){flush();send('exit');}
  else if(message.type==='state-updated'){state=normalize(message.state);for(const item of annotations())await draw(item);}
  else if(message.type==='snapshot-done')document.body.classList.remove('snapshot');
  else if(message.type==='busy')document.body.classList.toggle('working',!!message.enabled);
  else if(message.type==='font-imported'){config.fontSize=message.size;await loadFont(true);state.settings.font='custom';await restyle();changed();closePanel();}
  else if(message.type==='back'){
    if($('panel').open)closePanel();else if(selected)clearSelection();else if(document.body.classList.contains('zen')){document.body.classList.remove('zen');send('zen',{enabled:false});}else{flush();send('exit');}
  }else if(message.type==='probe'){
    if(!config?.debug)return;
    if(message.action==='select'){
      const {doc,index}=view.renderer.getContents().find(({doc})=>doc.body.textContent.includes(message.text))??{};
      if(!doc)throw Error('Test passage is not visible');
      const walker=doc.createTreeWalker(doc.body,NodeFilter.SHOW_TEXT);let node;
      while((node=walker.nextNode())){const offset=node.textContent.indexOf(message.text);if(offset>=0){const range=doc.createRange();range.setStart(node,offset);range.setEnd(node,offset+message.text.length);const selection=doc.defaultView.getSelection();selection.removeAllRanges();selection.addRange(range);selected=rangeTarget(doc,index);break;}}
    }else if(message.action==='highlight'){const item=ensureAnnotation(target());item.highlight=true;await draw(item);changed();clearSelection();}
    else if(message.action==='bookmark'){bookmark();}
    else if(message.action==='settings'){Object.assign(state.settings,message.settings);await restyle();changed();}
    else if(message.action==='next'){await view.next();}
    else if(message.action==='goto'){await view.goTo(message.value);}
    else if(message.action==='handwrite'){handwrite();}
    else if(message.action==='snapshot'){await captureSnapshot();}
    const resolved=annotations().map(item=>{try{const {index,anchor}=view.resolveCFI(item.value),entry=view.renderer.getContents().find(e=>e.index===index);return {value:item.value,text:entry?anchor(entry.doc).toString():null};}catch{return {value:item.value,text:null};}});
    send('probe',{id:message.id,title:text(book?.metadata?.title),location:state?.location,fraction:state?.fraction,settings:state?.settings,annotations:annotations(),
      visibility:document.visibilityState,
      resolved,documents:view?.renderer.getContents().map(({doc,index,overlayer})=>({index,marks:overlayer?.element.childElementCount??0,text:doc.body?.innerText?.slice(0,4000),selected:doc.defaultView.getSelection()?.toString(),scripts:doc.querySelectorAll('script').length,
        images:[...doc.images].map(i=>({width:i.naturalWidth,height:i.naturalHeight})),
        typography:(()=>{const p=doc.querySelector('p');if(!p)return null;const s=doc.defaultView.getComputedStyle(p);return {size:s.fontSize,font:s.fontFamily,weight:s.fontWeight,line:s.lineHeight,frameHeight:doc.defaultView.innerHeight};})(),
        url:doc.URL,bodyLength:doc.body?.textContent?.length,markup:doc.documentElement?.outerHTML?.slice(0,1800),
        canvases:[...doc.querySelectorAll('canvas')].map(c=>{let ink=0;try{const pixels=c.getContext('2d').getImageData(0,0,c.width,c.height).data;for(let i=0;i<pixels.length;i+=128)if(pixels[i]<220&&pixels[i+3]>0)ink++;}catch{}return {width:c.width,height:c.height,ink};})}))});
  }
}));
await browser.runtime.sendMessage({type:'offline'});send('hello');
