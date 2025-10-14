// chrome.runtime.onMessage.addListener((msg, sender) => {
//   if (!msg?.type) return;
//   chrome.runtime.sendNativeMessage(
//     'com.filepathbookmark.host', 
//     { 
//         type: msg.type, 
//         url: msg.url, 
//         ts: Date.now() 
//     },
//     () => void chrome.runtime.lastError
//   );
// });

// bg.js (지속 연결)
let port;
function ensurePort() {
  if (!port) {
    port = chrome.runtime.connectNative('com.filepathbookmark.host');
    console.log('[FPB][BG] connectNative called');
    port.onDisconnect.addListener(() => {
      console.warn('[FPB][BG] native disconnected', chrome.runtime.lastError?.message);
      port = null;
    });
    port.onMessage?.addListener?.((m) => console.log('[FPB][BG] from native:', m));
  }
  return port;
}

let lastOpen = 0;
chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  console.log('[FPB][BG] got msg:', msg);

  if (msg?.type === 'FILE_DIALOG_OPENING') {
    const now = Date.now();
    if (now - lastOpen < 500) { sendResponse({ ok: true, dedup: true }); return; }
    lastOpen = now;
  }

  try {
    ensurePort().postMessage({ type: msg.type, url: msg.url, ts: Date.now() });
    sendResponse({ ok: true });
  } catch (e) {
    console.warn('[FPB][BG] postMessage failed:', String(e));
    sendResponse({ ok: false, error: String(e) });
  }
  return true; // 비동기 응답 채널 유지
});
