#!/usr/bin/env node
// Exercise the production extension's popup policy, including event ordering.
const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert/strict');
(async()=>{
  const root=path.resolve(__dirname,'..');
  const {allowedRequest}=require(path.join(root,'build/browser-test-assets/policy.cjs'));
  const handlers={};
  const event=name=>({addListener(fn){handlers[name]=fn;}});
  const browser={runtime:{getURL:()=> 'moz-extension://test/',onMessage:event('message')},
    webRequest:{onBeforeRequest:event('request')},tabs:{onRemoved:event('removed')}};
  const source=fs.readFileSync(path.join(root,'renderer/browser/background.js'),'utf8').replace(/^import .*;\s*$/gm,'');
  vm.runInNewContext(source,{browser,allowedRequest,URL,AbortController,setTimeout,clearTimeout});
  const configure=(id,offline=false)=>handlers.message({type:'configure',offline,archive:offline?'file:///saved/page.html':''},
    {tab:{id},frameId:0,url:'moz-extension://test/bootstrap.html'});
  const authorize=(id,url)=>handlers.message({type:'authorize-popup',url},{tab:{id},frameId:0,url:'https://example.org/'});
  const request=(id,url,type='main_frame')=>handlers.request({tabId:id,url,type});
  await configure(1);await configure(2,true);
  await authorize(1,'https://example.org/login#fragment');
  assert.equal((await request(3,'https://example.org/other')).cancel,true,'a different URL cannot consume a grant');
  assert.equal((await request(3,'https://example.org/login','image')).cancel,true,'subresources cannot consume a grant');
  assert.equal((await request(3,'https://example.org/login')).cancel,false,'one authorized popup receives the online policy');
  assert.equal((await request(3,'http://example.org/')).cancel,true,'a popup retains HTTPS-only protection');
  assert.equal((await request(4,'https://example.org/login')).cancel,true,'a grant cannot be reused by another tab');
  await assert.rejects(()=>authorize(2,'https://example.org/leak'),'an offline opener cannot grant network access');
  assert.equal((await request(2,'file:///saved/page.html')).cancel,false,'the offline archive remains available');
  assert.equal((await request(6,'https://example.org/')).cancel,true,'an unknown tab fails closed');
  assert.equal((await request(7,'https://example.org/')).cancel,true,'an unknown tab fails closed');
  await authorize(1,'https://example.org/pending');handlers.removed(1);
  assert.equal((await request(8,'https://example.org/pending')).cancel,true,'closing the granting tab revokes unused grants');
  handlers.removed(3);
  assert.equal((await request(3,'https://example.org/')).cancel,true,'a closed popup cannot reuse its prior policy');
  console.log('Browser popup policy: 11 checks passed (exact URL grants, single use, offline isolation and retirement).');
})().catch(error=>{console.error(error);process.exitCode=1;});
