'use client';

import { useEffect, useState, useSyncExternalStore } from 'react';

function subscribeDisplay(listener) {
  const display = window.matchMedia('(display-mode: standalone)');
  display.addEventListener('change', listener);
  return () => display.removeEventListener('change', listener);
}

function environmentSnapshot() {
  if (window.matchMedia('(display-mode: standalone)').matches || navigator.standalone === true) return 'installed';
  return /iPad|iPhone|iPod/.test(navigator.userAgent) ||
    (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1) ? 'ios' : 'browser';
}

function serverSnapshot() { return 'installed'; }

export default function InstallApp({ visible, onNotice }) {
  const [prompt, setPrompt] = useState(null);
  const [installed, setInstalled] = useState(false);
  const environment = useSyncExternalStore(subscribeDisplay, environmentSnapshot, serverSnapshot);
  const ios = environment === 'ios';
  const [busy, setBusy] = useState(false);
  const [showHelp, setShowHelp] = useState(false);

  useEffect(() => {
    function ready(event) {
      event.preventDefault();
      setPrompt(event);
    }
    function complete() {
      setInstalled(true);
      setPrompt(null);
      setShowHelp(false);
    }
    window.addEventListener('beforeinstallprompt', ready);
    window.addEventListener('appinstalled', complete);
    if ('serviceWorker' in navigator && window.isSecureContext) {
      navigator.serviceWorker.register('/vote-push.js').catch(() => {
        // Installation guidance remains available if registration needs a retry.
      });
    }
    return () => {
      window.removeEventListener('beforeinstallprompt', ready);
      window.removeEventListener('appinstalled', complete);
    };
  }, []);

  async function install() {
    if (!prompt) { setShowHelp(value => !value); return; }
    const request = prompt;
    setBusy(true);
    try {
      await request.prompt();
      const choice = await request.userChoice;
      if (choice.outcome === 'accepted') setInstalled(true);
    } catch {
      onNotice('브라우저 메뉴에서 홈 화면에 추가를 선택해 주세요.');
    } finally {
      setPrompt(null);
      setBusy(false);
    }
  }

  if (!visible || installed || environment === 'installed') return null;
  return <section className="install-app" aria-label="TEAM UP 설치">
    <div className="install-app-heading">
      <div><strong>TEAM UP을 홈 화면에</strong><p>아이콘을 눌러 바로 시작하세요.</p></div>
      <button type="button" className="text-button" disabled={busy} onClick={install}
        aria-expanded={prompt ? undefined : showHelp} aria-controls={prompt ? undefined : 'install-app-help'}>
        {prompt ? '설치하기' : showHelp ? '안내 닫기' : '설치 방법'}
      </button>
    </div>
    {showHelp && <div id="install-app-help" className="install-app-help">
      {ios ? <p>Safari에서 이 사이트를 열고 <strong>공유 → 홈 화면에 추가 → 추가</strong>를 선택하세요. <strong>웹 앱으로 열기</strong>가 표시되면 켜 주세요.</p> :
        <p>Chrome 또는 Edge의 메뉴에서 <strong>앱 설치</strong> 또는 <strong>홈 화면에 추가</strong>를 선택하세요. 설치 항목이 보이지 않으면 지원하는 브라우저에서 다시 열어 주세요.</p>}
      <p>카카오톡·네이버 앱 안에서 열었다면 메뉴에서 외부 브라우저로 열어 주세요.</p>
    </div>}
  </section>;
}
