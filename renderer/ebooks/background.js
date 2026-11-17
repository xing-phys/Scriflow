// Only this extension's book tabs are restricted; the separate browser is unaffected.
const tabs = new Set(), origin = browser.runtime.getURL('');
browser.runtime.onMessage.addListener((message, sender) => {
  if (message.type !== 'offline' || !sender.url?.startsWith(origin) || sender.tab?.id == null) throw Error('Invalid book owner');
  tabs.add(sender.tab.id); return Promise.resolve(true);
});
browser.tabs.onRemoved.addListener(id => tabs.delete(id));
browser.webRequest.onBeforeRequest.addListener(request => {
  if (!tabs.has(request.tabId) && !request.originUrl?.startsWith(origin) && !request.documentUrl?.startsWith(origin)) return {};
  return { cancel: !request.url.startsWith(origin) && !/^(blob:|data:|about:)/.test(request.url) };
}, { urls: ['<all_urls>'] }, ['blocking']);
