#!/usr/bin/env node
// Production reader modules, real files, browser layout and native-port simulation.
const fs=require('fs'),path=require('path'),http=require('http'),assert=require('assert/strict');
const {spawnSync}=require('child_process');
const root=path.resolve(__dirname,'..'),source=path.join(root,'renderer/ebooks'),fixtures=path.join(root,'build/ebook-fixtures');
const puppeteer=require(path.join(root,'.tools/markdown-renderer/node_modules/puppeteer-core'));
const generated=spawnSync('python3',['tests/make-ebook-fixtures.py'],{cwd:root,stdio:'inherit'});if(generated.status)process.exit(generated.status);
let browser,server,assertions=0;
const check=(condition,message)=>{assert(condition,message);assertions++;console.log('PASS:',message);};
(async()=>{
  const blockedExternal=[];
  server=http.createServer((req,res)=>{
    const name=decodeURIComponent(new URL(req.url,'http://localhost').pathname).replace(/^\//,'')||'reader.html';
    const file=path.resolve(source,name);
    if(!file.startsWith(source+path.sep)||!fs.existsSync(file)){res.writeHead(404);res.end();return;}
    res.setHeader('Content-Type',name.endsWith('.js')?'text/javascript':name.endsWith('.wasm')?'application/wasm':name.endsWith('.css')?'text/css':'text/html');
    res.setHeader('Content-Security-Policy',"script-src 'self' 'wasm-unsafe-eval'; object-src 'none'; connect-src 'self' blob: data:; img-src blob: data:; style-src 'self' 'unsafe-inline' blob: data:; font-src blob: data:; frame-src 'self' blob: data:; base-uri 'none'");
    res.end(fs.readFileSync(file));
  });await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage','--disable-background-networking']});
  async function open(name,format,saved={},reject=false){
    const file=fs.readFileSync(path.join(fixtures,name)),page=await browser.newPage();await page.setViewport({width:700,height:950,hasTouch:true});
    await page.setRequestInterception(true);
    page.on('request',request=>{
      if(request.url().startsWith(`http://127.0.0.1:${server.address().port}/`)||/^(blob:|data:)/.test(request.url()))request.continue();
      else{blockedExternal.push(request.url());request.abort();}
    });
    const data={messages:[],saved:null,ready:null,error:null,page};
    const reply=message=>page.evaluate(message=>{for(const listener of globalThis.__nativeListeners)listener(message);},message);
    await page.exposeFunction('__native',async message=>{
      data.messages.push(message);
      if(message.type==='hello')await reply({type:'open',id:'a'.repeat(64),name,format,size:file.length,state:saved,debug:true,fontSize:0});
      else if(message.type==='read')await reply({type:'read-result',id:message.id,data:file.subarray(message.offset,message.offset+message.length).toString('base64')});
      else if(message.type==='state')data.saved=message.state;
      else if(message.type==='ready')data.ready=message;
      else if(message.type==='error')data.error=message.message+'\n'+(message.stack||'');
    });
    await page.evaluateOnNewDocument(()=>{
      globalThis.__nativeListeners=[];
      globalThis.browser={runtime:{connectNative:()=>({postMessage:message=>globalThis.__native(message),onMessage:{addListener:listener=>globalThis.__nativeListeners.push(listener)}}),sendMessage:async()=>true}};
    });
    page.on('pageerror',e=>{data.error=String(e.stack||e);});
    await page.goto(`http://127.0.0.1:${server.address().port}/reader.html`,{waitUntil:'domcontentloaded'});
    for(let n=0;n<300&&!data.ready&&!data.error;n++)await new Promise(r=>setTimeout(r,50));
    if(reject){check(!!data.error&&/DRM|encryption/.test(data.error),name+' is rejected with a clear encryption message');return data;}
    if(data.error)throw Error(name+': '+data.error);assert(data.ready,name+' opened');
    data.reply=reply;
    data.probe=async(action={})=>{
      const id=Math.random().toString(36),before=data.messages.length;await reply({type:'probe',id,...action});
      for(let n=0;n<100;n++){const value=data.messages.slice(before).find(m=>m.type==='probe'&&m.id===id);if(value)return value;if(data.error)throw Error(data.error);await new Promise(r=>setTimeout(r,30));}
      throw Error('Probe timed out: '+name);
    };return data;
  }
  try{
    const epub=await open('quantum.epub','epub');
    check(epub.ready.title==='Quantum Reading Fixture'&&epub.ready.sections===2,'EPUB metadata and spine parsed from actual ZIP');
    let probe=await epub.probe();check(probe.documents.some(d=>d.text.includes('uncertainty principle')&&d.text.includes('中文')),'EPUB renders scientific prose and CJK text');
    const viewport=await epub.page.$eval('#reader',e=>e.clientHeight);
    check(probe.documents[0].typography.frameHeight>viewport-60,'The page uses the reading area with valid CSS margin lengths');
    check(probe.documents[0].images.some(i=>i.width===320),'EPUB illustrations decode after the sanitized document loads');
    probe=await epub.probe({action:'bookmark'});
    check(probe.annotations.length===1&&probe.annotations[0].bookmark&&probe.documents[0].marks===0,'Bookmarking a position does not mark the whole visible page');
    probe=await epub.probe({action:'bookmark'});check(probe.annotations.length===0,'Removing a plain bookmark leaves no empty annotation');
    await epub.probe({action:'select',text:'The uncertainty principle'});await epub.probe({action:'highlight'});
    probe=await epub.probe();const anchor=probe.annotations[0].value;
    check(probe.resolved.some(a=>a.value===anchor&&a.text==='The uncertainty principle'),'Highlight resolves to the selected text range');
    await epub.probe({action:'settings',settings:{size:34,font:'serif',line:1.8,margin:40}});
    await epub.probe({action:'goto',value:anchor});probe=await epub.probe();
    check(probe.settings.size===34&&probe.resolved.some(a=>a.value===anchor&&a.text==='The uncertainty principle'),'Highlight stays attached after font and margin reflow');
    check(probe.documents[0].typography.size==='34px'&&probe.documents[0].typography.font==='serif','Font size and family override publisher paragraph styles');
    await epub.probe({action:'next'});await epub.reply({type:'flush'});await new Promise(r=>setTimeout(r,100));
    const saved=epub.saved;check(saved?.location&&saved.settings.size===34,'Reading state contains a text locator and per-book typography');
    await epub.page.close();
    const restored=await open('quantum.epub','epub',saved);probe=await restored.probe();
    check(probe.annotations[0].value===anchor&&probe.settings.size===34&&probe.fraction>0,'A new reader restores annotations, settings and reading progress');
    await restored.reply({type:'command',action:'contents'});await restored.page.waitForSelector('#panel[open]');check((await restored.page.$$('.contents-title')).length===2,'Contents lists both EPUB chapters');await restored.page.click('#close-panel');
    await restored.reply({type:'command',action:'search'});await restored.page.waitForSelector('input[aria-label="Search book text"]');await restored.page.type('input[aria-label="Search book text"]','entanglement');await restored.page.evaluate(()=>[...document.querySelectorAll('#panel-content button')].find(b=>b.textContent==='Find').click());
    await restored.page.waitForFunction(()=>document.querySelectorAll('#panel-content div button').length>0,{timeout:30000});check(true,'Full-book search returns actual text locations');await restored.page.click('#close-panel');
    await restored.page.screenshot({path:path.join(root,'build/ebook-reader.png')});await restored.page.close();
    const spread=await open('quantum.epub','epub');
    await spread.reply({type:'command',action:'type'});await spread.page.waitForSelector('select[aria-label="Columns"]');
    await spread.page.select('select[aria-label="Columns"]','2');await new Promise(r=>setTimeout(r,500));
    probe=await spread.probe();
    const visibleColumns=await spread.page.evaluate(()=>{
      const doc=document.querySelector('foliate-view').renderer.getContents()[0].doc;
      const frame=doc.defaultView.frameElement.getBoundingClientRect(), area=document.querySelector('#reader').getBoundingClientRect();
      const positions=[];for(const p of doc.querySelectorAll('p'))for(const rect of p.getClientRects()){
        const x=frame.left+rect.left,y=frame.top+rect.top;
        if(rect.width>10&&x>=area.left&&x<area.right&&y>=area.top&&y<area.bottom)positions.push(x-area.left);
      }return positions;
    });
    check(probe.settings.columns===2&&probe.settings.flow==='paginated'&&visibleColumns.some(x=>x<350)&&visibleColumns.some(x=>x>350),'Two columns render visible text on both halves in portrait');
    await spread.page.select('select[aria-label="Reading"]','scrolled');await new Promise(r=>setTimeout(r,200));probe=await spread.probe();
    check(probe.settings.columns===1&&probe.settings.flow==='scrolled'&&await spread.page.$eval('select[aria-label="Columns"]',e=>e.value)==='1','Continuous scroll switches to one flowing column');
    await spread.page.select('select[aria-label="Columns"]','2');await new Promise(r=>setTimeout(r,200));probe=await spread.probe();
    check(probe.settings.columns===2&&probe.settings.flow==='paginated','Choosing two columns restores page turns');
    await spread.page.click('#close-panel');
    const first=probe.location;await spread.page.touchscreen.tap(540,700);await new Promise(r=>setTimeout(r,650));probe=await spread.probe();const second=probe.location;
    check(first!==second,'A right-hand finger tap turns to the next spread');
    await spread.page.touchscreen.tap(100,700);await new Promise(r=>setTimeout(r,650));probe=await spread.probe();const third=probe.location;
    check(second!==third,'A bottom-left finger tap turns to the next spread');
    await spread.page.touchscreen.tap(100,180);await new Promise(r=>setTimeout(r,650));probe=await spread.probe();
    check(probe.location!==third,'A top-left finger tap turns to the previous spread');
    await spread.probe({action:'goto',value:first});await new Promise(r=>setTimeout(r,300));
    const link=await spread.page.evaluate(()=>{
      const doc=document.querySelector('foliate-view').renderer.getContents()[0].doc,frame=doc.defaultView.frameElement.getBoundingClientRect();
      const a=[...doc.querySelectorAll('a')].find(e=>e.textContent==='Linked passage'),r=a.getBoundingClientRect();
      return {x:frame.left+r.left+r.width/2,y:frame.top+r.top+r.height/2};
    });
    await spread.page.touchscreen.tap(link.x,link.y);await new Promise(r=>setTimeout(r,800));probe=await spread.probe();
    check(probe.documents.some(d=>d.index===1),'Tapping a book link follows it instead of turning the page');
    await spread.probe({action:'select',text:'The uncertainty principle'});probe=await spread.probe();const selection=probe.documents.find(d=>d.selected)?.selected,selectedLocation=probe.location;
    await spread.page.touchscreen.tap(540,700);await new Promise(r=>setTimeout(r,500));probe=await spread.probe();
    check(selection&&probe.location===selectedLocation,'An existing text selection prevents a page-turn tap');
    await spread.page.close();
    for(const [width,height] of [[700,950],[950,700]])for(const columns of [1,2]){
      const reader=await open('quantum.epub','epub');await reader.page.setViewport({width,height,hasTouch:true});
      await reader.probe({action:'settings',settings:{columns,flow:'paginated'}});await new Promise(r=>setTimeout(r,300));
      const start=(await reader.probe()).location;
      await reader.page.touchscreen.touchStart(width*.75,height*.35);
      await reader.page.touchscreen.touchMove(width*.75+7,height*.35+3);
      await reader.page.touchscreen.touchEnd();await new Promise(r=>setTimeout(r,450));
      const next=(await reader.probe()).location;
      check(next!==start,`Jitter-tolerant right tap advances ${columns} column(s) at ${width}×${height}`);
      await reader.page.touchscreen.tap(width*.2,height*.75);await new Promise(r=>setTimeout(r,450));
      const afterTwo=(await reader.probe()).location;
      check(afterTwo!==next,`Bottom-left tap advances ${columns} column(s) at ${width}×${height}`);
      await reader.page.touchscreen.tap(width*.2,height*.2);await new Promise(r=>setTimeout(r,450));
      check((await reader.probe()).location!==afterTwo,`Top-left tap goes back ${columns} column(s) at ${width}×${height}`);
      await reader.probe({action:'goto',value:start});await new Promise(r=>setTimeout(r,350));
      await reader.page.touchscreen.tap(width*.75,height*.35);await reader.page.touchscreen.tap(width*.75,height*.35);
      await new Promise(r=>setTimeout(r,700));
      check((await reader.probe()).location===afterTwo,`Rapid taps queue two turns at ${width}×${height} with ${columns} column(s)`);
      await reader.page.close();
    }
    const scrolling=await open('quantum.epub','epub');
    await scrolling.probe({action:'settings',settings:{flow:'scrolled'}});await new Promise(r=>setTimeout(r,300));
    const scrollStart=(await scrolling.probe()).location;
    await scrolling.page.touchscreen.tap(550,300);await new Promise(r=>setTimeout(r,600));
    const scrollNext=(await scrolling.probe()).location;
    check(scrollStart!==scrollNext,'A right tap advances the continuous ebook view');
    await scrolling.page.touchscreen.tap(120,180);await new Promise(r=>setTimeout(r,600));
    check((await scrolling.probe()).location!==scrollNext,'A top-left tap goes back in the continuous ebook view');
    await scrolling.page.close();
    const nested=await open('nested.epub','epub');await nested.reply({type:'command',action:'contents'});await nested.page.waitForSelector('#panel[open]');
    let labels=await nested.page.$$eval('.contents-title',nodes=>nodes.map(n=>n.textContent));
    check(labels.includes('Spin chains')&&labels.includes('Quantum information')&&!labels.includes('Other section')&&!labels.includes('Deep section'),'Contents collapses unrelated and deeper branches by default');
    await nested.page.click('[aria-label="Expand Quantum information"]');labels=await nested.page.$$eval('.contents-title',nodes=>nodes.map(n=>n.textContent));
    check(labels.includes('Other section'),'Contents disclosure expands a branch without navigating');
    await nested.page.click('[aria-label="Collapse Quantum information"]');labels=await nested.page.$$eval('.contents-title',nodes=>nodes.map(n=>n.textContent));
    check(!labels.includes('Other section'),'Contents disclosure collapses a branch');
    await nested.page.close();
    for(const [name,format] of [['quantum.fb2','fb2'],['quantum.fb2.zip','fbz'],['quantum.txt','txt'],['quantum.html','html'],['comic.cbz','cbz'],['sample-cp1252.mobi','mobi'],['sample-ncx.mobi','mobi'],['sample-obfuscated-fonts.mobi','mobi'],...(fs.existsSync(path.join(fixtures,'sample-kf8.azw3'))?[['sample-kf8.azw3','azw3']]:[]),['chicken.djvu','djvu'],['DjVu3Spec_bundled.djvu','djvu'],['large-bundled.djvu','djvu']]){
      const value=await open(name,format);let p=await value.probe();
      if(format==='djvu'){
        for(let n=0;n<60&&!p.documents.some(d=>d.canvases.some(c=>c.ink>0));n++){await new Promise(r=>setTimeout(r,100));p=await value.probe();}
        check(p.documents.some(d=>d.canvases.some(c=>c.width>0&&c.height>0&&c.ink>0)),name+' decodes and paints real DjVu pixels');
        if(name==='large-bundled.djvu'){await value.reply({type:'command',action:'search'});await value.page.waitForSelector('#panel[open]');check((await value.page.$eval('#panel-content',e=>e.textContent)).includes('loaded page by page'),'Large DjVu uses random-access mode and explains its text-search limit');}
      }else check(value.ready.sections>0&&p.documents.some(d=>d.text?.trim()||d.images.some(i=>i.width>0)),name+' opens through its actual format parser and renders content');
      if(format==='html')check(p.documents.every(d=>d.scripts===0),'HTML script elements are removed before rendering');
      if(format==='cbz')check(value.ready.title==='comic','CBZ metadata keeps the parser extension out of the saved shelf title');
      await value.page.close();
    }
    for(const [name,format] of [['sample-drm-v1.mobi','mobi'],['encrypted.epub','epub']]){const rejected=await open(name,format,{},true);await rejected.page.close();}
    const evil=await open('scripted.epub','epub'),ep=await evil.probe();
    check(ep.documents.every(d=>d.scripts===0),'Scripted EPUB content is sanitized');
    check(await evil.page.evaluate(()=>!globalThis.__bookScriptExecuted),'Book content cannot execute parent-page scripts');await evil.page.close();
    check(blockedExternal.length===0,'Book security policies prevent external content requests before the network boundary');
    fs.writeFileSync(path.join(root,'build/ebook-test-result.json'),JSON.stringify({passed:true,assertions},null,2)+'\n');
    console.log('Offline ebooks:',assertions,'browser assertions passed.');
  }finally{await browser.close();await new Promise(r=>server.close(r));}
})().catch(error=>{console.error(error);process.exitCode=1;});
