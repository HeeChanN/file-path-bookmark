// 1) 전통적인 <input type="file"> 클릭 감지
document.addEventListener('click', (e) => {
  const el = e.target.closest('input[type="file"]');
  if (!el) return;

  console.log('[FPB][CS] file input clicked:', location.href);

  chrome.runtime.sendMessage(
    { type: 'FILE_DIALOG_OPENING', url: location.href },
    (res) => {
      if (chrome.runtime.lastError) {
        console.log('[FPB][CS] sendMessage error:', chrome.runtime.lastError.message);
      } else {
        console.log('[FPB][CS] ack from BG:', res);
      }
    }
  );
}, true);



// document.addEventListener('click', (e) => {
//   const el = e.target?.closest?.('input[type="file"]');
//   if (!el) return;

//   console.log('[FPB] file input clicked at:', location.href);
//   try {
//     if (!chrome?.runtime?.id) return; // 컨텍스트 무효화 방지 가드
//     chrome.runtime.sendMessage({ type: 'FILE_DIALOG_OPENING', url: location.href });
//   } catch (err) {
//     console.log('[FPB] sendMessage failed:', err?.message || err);
//   }
// }, true);

// // 2) File System Access API 사용 사이트 대응: showOpenFilePicker 패치
// (function patchShowPicker(){
//   const inject = () => {
//     const orig = window.showOpenFilePicker;
//     if (typeof orig === 'function') {
//       window.showOpenFilePicker = async function(...args){
//         window.postMessage({ __fromExt: true, type: 'FILE_DIALOG_OPENING' }, '*');
//         try {
//           const res = await orig.apply(this, args);
//           window.postMessage({ __fromExt: true, type: 'FILE_DIALOG_CLOSED' }, '*');
//           return res;
//         } catch (e) {
//           window.postMessage({ __fromExt: true, type: 'FILE_DIALOG_CLOSED' }, '*');
//           throw e;
//         }
//       };
//     }
//   };
//   // 페이지 컨텍스트에 주입
//   const s = document.createElement('script');
//   s.textContent = `(${inject})();`;
//   (document.head || document.documentElement).appendChild(s);
//   window.addEventListener('message', (ev) => {
//     if (ev.data?.__fromExt) chrome.runtime.sendMessage({ type: ev.data.type, url: location.href });
//   });
// })();
