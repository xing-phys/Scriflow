#!/usr/bin/env node
// Exercise the production extension's popup policy, including event ordering.
const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert/strict');
(async()=>{
  const root=path.resolve(__dirname,'..');
  const {allowedRequest}=require(path.join(root,'build/browser-test-assets/policy.cjs'));
  const handlers={};
  const event=name=>({addListener(fn){handlers[name]=fn;}});
  const browser={runtime:{getURL:()=> 'moz-extension://test/',onMessage:event('message')},
    webRequest:{onBeforeRequest:event('request'),onCompleted:event('completed'),onErrorOccurred:event('error')},tabs:{onRemoved:event('removed')}};
  const source=fs.readFileSync(path.join(root,'renderer/browser/background.js'),'utf8').replace(/^import .*;\s*$/gm,'');
  const fetched=[];
  const fetch=async(url,options)=>{fetched.push({url,options});return {ok:true,url,headers:{get:()=>null},body:{getReader:()=>({read:async()=>({done:true})})}};};
  vm.runInNewContext(source,{browser,allowedRequest,URL,AbortController,setTimeout,clearTimeout,fetch,btoa:value=>Buffer.from(value,'binary').toString('base64')});
  const configure=(id,offline=false)=>handlers.message({type:'configure',offline,archive:offline?'file:///saved/page.html':''},
    {tab:{id},frameId:0,url:'moz-extension://test/bootstrap.html'});
  const authorize=(id,url)=>handlers.message({type:'authorize-popup',url},{tab:{id},frameId:0,url:'https://example.org/'});
  const request=(id,url,type='main_frame')=>handlers.request({tabId:id,url,type});
  await configure(1);await configure(2,true);
  await authorize(1,'https://example.org/login#fragment');
  assert.equal((await request(3,'https://example.org/other')).cancel,true,'a different URL cannot consume a grant');
  assert.equal((await request(3,'https://example.org/login','image')).cancel,true,'subresources cannot consume a grant');
  assert.equal((await request(3,'https://example.org/login')).cancel,false,'one authorized popup receives the online policy');
  assert.equal((await request(3,'http://example.org/')).cancel,false,'a configured online popup can follow an HTTP link');
  assert.equal((await request(4,'https://example.org/login')).cancel,true,'a grant cannot be reused by another tab');
  await assert.rejects(()=>authorize(2,'https://example.org/leak'),'an offline opener cannot grant network access');
  await assert.rejects(()=>authorize(1,'http://user:secret@example.org/'),'popup grants reject embedded credentials');
  await authorize(1,'http://example.org/legacy');
  assert.equal((await request(9,'http://example.org/legacy')).cancel,false,'a user-authorized HTTP popup opens once');
  assert.equal((await request(2,'file:///saved/page.html')).cancel,false,'the offline archive remains available');
  assert.equal((await request(6,'https://example.org/')).cancel,true,'an unknown tab fails closed');
  assert.equal((await request(7,'https://example.org/')).cancel,true,'an unknown tab fails closed');
  await authorize(1,'https://example.org/pending');handlers.removed(1);
  assert.equal((await request(8,'https://example.org/pending')).cancel,true,'closing the granting tab revokes unused grants');
  handlers.removed(3);
  assert.equal((await request(3,'https://example.org/')).cancel,true,'a closed popup cannot reuse its prior policy');
  await configure(10); await configure(11);
  const id='11111111-1111-4111-8111-111111111111';
  const send=(tab,url,message)=>handlers.message(message,{tab:{id:tab},frameId:0,url});
  await send(10,'https://example.org/',{type:'begin',id});
  await assert.rejects(()=>send(10,'https://example.org/',{type:'fetch',id,url:'http://example.org/asset'}),'capturing HTTPS cannot fetch HTTP resources');
  await send(10,'https://example.org/',{type:'fetch',id,url:'https://example.org/asset'});
  assert.equal(fetched.at(-1).options.redirect,undefined,'ordinary secure resource redirects remain enabled');
  assert.equal(handlers.request({tabId:-1,requestId:'secure',url:'https://example.org/asset'}).cancel,false,'secure extension fetch can start');
  assert.equal(handlers.request({tabId:-1,requestId:'secure',url:'https://other.example/asset'}).cancel,false,'secure resource redirects remain available');
  assert.equal(handlers.request({tabId:-1,requestId:'secure',url:'http://example.org/asset'}).cancel,true,'an extension fetch cannot redirect from HTTPS to HTTP');
  handlers.error({requestId:'secure'});
  await send(11,'http://example.org/',{type:'begin',id});
  await send(11,'http://example.org/',{type:'fetch',id,url:'http://example.org/asset'});
  assert.equal(fetched.at(-1).url,'http://example.org/asset','HTTP captures can retrieve their HTTP resources');
  console.log('Browser popup and capture policy: exact one-use grants, HTTP links, secure capture boundaries, offline isolation and retirement passed.');
})().catch(error=>{console.error(error);process.exitCode=1;});
