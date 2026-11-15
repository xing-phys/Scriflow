// Only the trusted background page connects to this app-owned data-management port.
const dataPort = browser.runtime.connectNative('scriflow_data');
dataPort.onMessage.addListener(async message => {
  if (message.type !== 'sites') return;
  try {
    const cookies = await browser.cookies.getAll({}), domains = new Map();
    for (const cookie of cookies) {
      const host = cookie.domain.replace(/^\./, '').toLowerCase();
      domains.set(host, (domains.get(host) || 0) + 1);
    }
    dataPort.postMessage({ type: 'sites', id: message.id,
      sites: [...domains].sort(([a], [b]) => a.localeCompare(b)).slice(0, 2000).map(([host, count]) => ({ host, count })) });
  } catch (error) { dataPort.postMessage({ type: 'error', id: message.id }); }
});
