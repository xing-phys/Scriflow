console.log('BrowserDataTest background starting');
const port = browser.runtime.connectNative('scriflow_test');
port.onMessage.addListener(async message => {
  console.log('BrowserDataTest command', message.type);
  try {
    if (message.type === 'seed') for (const host of ['first.scriflow-test.example', 'second.scriflow-test.example']) {
      await browser.cookies.set({ url: 'https://' + host + '/', name: 'scriflow_fixture', value: 'synthetic-test-data', secure: true, expirationDate: Date.now() / 1000 + 3600 });
    }
    port.postMessage({ type: 'ready' });
  } catch (error) { port.postMessage({ type: 'error', message: String(error.message) }); }
});
