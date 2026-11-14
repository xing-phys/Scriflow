const port = browser.runtime.connectNative('scriflow');
port.onMessage.addListener(async message => {
  if (message.type !== 'configure') return;
  try {
    await browser.runtime.sendMessage(message);
    port.postMessage({ type: 'configured' });
  } catch (error) { port.postMessage({ type: 'error', message: String(error.message) }); }
});
