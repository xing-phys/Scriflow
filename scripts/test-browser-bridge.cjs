#!/usr/bin/env node
// Run the shipped document-start bridge against a page whose load event never finishes.
const fs=require('fs'),path=require('path'),http=require('http'),assert=require('assert/strict');
const root=path.resolve(__dirname,'..');
const puppeteer=require(path.join(root,'.tools/markdown-renderer/node_modules/puppeteer-core'));
(async()=>{
 let browser,server,checks=0;const pending=new Set();
 const check=(condition,message)=>{assert.ok(condition,message);checks++;};
 try{
  server=http.createServer((request,response)=>{
   if(request.url==='/pending.svg'){pending.add(response);return;}
   response.setHeader('content-type','text/html; charset=utf-8');
   response.end('<!doctype html><title>Readable before load</title><h1>Visible quantum notes</h1><p>'+('Physics 🧪 '.repeat(14000))+'</p><img src="/pending.svg">');
  });await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
  const page=await browser.newPage();
  await page.evaluateOnNewDocument(()=>{
   window.ports=[];window.messages=[];window.commands=[];window.holdAcks=false;
   window.browser={runtime:{connectNative(){const port={onMessage:{addListener(fn){port.receive=fn;}},onDisconnect:{addListener(fn){port.closed=fn;}},disconnect(){port.closed?.();},postMessage(value){messages.push(value);if(value.type==='chunk'&&!holdAcks)queueMicrotask(()=>port.receive({type:'ack',id:value.id,sequence:value.sequence}));}};ports.push(port);return port;},async sendMessage(value){commands.push(value);if(value.type==='fetch')throw Error('Fixture resource unavailable');return true;}}};
   window.command=value=>ports.at(-1).receive(value);
  });
  await page.evaluateOnNewDocument(fs.readFileSync(path.join(root,'app/build/generated/browser-assets/browser-extension/content.js'),'utf8'));
  await page.goto('http://127.0.0.1:'+server.address().port+'/article',{waitUntil:'domcontentloaded'});
  check(await page.evaluate(()=>document.readyState!=='complete'),'fixture is visibly readable while its load event is blocked');
  check(await page.evaluate(()=>messages.some(m=>m.type==='document'&&m.readable)),'capture bridge reports DOM readiness before load completes');
  const id='11111111-1111-4111-8111-111111111111';
  await page.evaluate(id=>command({type:'capture',id}),id);
  await page.waitForFunction(id=>messages.some(m=>m.type==='complete'&&m.id===id),{},id);
  const capture=await page.evaluate(id=>messages.filter(m=>m.id===id),id);
  const chunks=capture.filter(m=>m.type==='chunk');
  check(chunks.length>1&&chunks.every((m,i)=>m.sequence===i),'writer acknowledgements preserve multiple UTF-8 chunks');
  check(chunks.map(m=>m.text).join('').includes('Visible quantum notes'),'already rendered content is captured without waiting for network idle');
  check(await page.evaluate(()=>document.readyState!=='complete'),'capture completes with the original network resource still pending');
  await page.evaluate(()=>ports.at(-1).disconnect());
  await page.waitForFunction(()=>ports.length===2);
  check(await page.evaluate(()=>messages.filter(m=>m.type==='document').length>=3),'a disconnected bridge reconnects and re-identifies its document');
  const starts=await page.evaluate(()=>commands.filter(m=>m.type==='begin').length);
  await page.evaluate(()=>ports[0].receive({type:'capture',id:'stale'}));
  check(await page.evaluate(()=>commands.filter(m=>m.type==='begin').length)===starts,'messages on a retired port cannot start captures');
  await page.evaluate(()=>dispatchEvent(new PageTransitionEvent('pagehide',{persisted:true})));
  await new Promise(resolve=>setTimeout(resolve,350));
  check(await page.evaluate(()=>ports.length===2),'a hidden cached page does not reconnect in the background');
  await page.evaluate(()=>dispatchEvent(new PageTransitionEvent('pageshow',{persisted:true})));
  check(await page.evaluate(()=>ports.length===3),'restoring a cached document reopens its capture connection');
  const canceled='22222222-2222-4222-8222-222222222222';
  await page.evaluate(id=>{holdAcks=true;command({type:'capture',id});},canceled);
  await page.waitForFunction(id=>messages.some(m=>m.type==='chunk'&&m.id===id),{},canceled);
  await page.evaluate(id=>command({type:'ack',id:'retired-capture',sequence:0}),canceled);
  await new Promise(resolve=>setTimeout(resolve,50));
  check(await page.evaluate(id=>messages.filter(m=>m.type==='chunk'&&m.id===id).length,canceled)===1,'stale writer acknowledgements cannot acknowledge a newer capture');
  await page.evaluate(id=>command({type:'cancel',id}),canceled);
  check(await page.evaluate(id=>!messages.some(m=>m.type==='complete'&&m.id===id),canceled),'canceling cannot publish a partial archive');
  const retry='33333333-3333-4333-8333-333333333333';
  await page.evaluate(id=>{holdAcks=false;command({type:'capture',id});},retry);
  await page.waitForFunction(id=>messages.some(m=>m.type==='complete'&&m.id===id),{},retry);
  check(true,'capture recovers after cancellation on a restored page');
  console.log('Browser capture bridge: '+checks+' checks passed (early readiness, pending resources, reconnect, cached restoration, cancellation and writer ownership).');
 }finally{if(browser)await browser.close();for(const response of pending)response.destroy();if(server)await new Promise(resolve=>server.close(resolve));}
})().catch(error=>{console.error(error);process.exitCode=1;});
