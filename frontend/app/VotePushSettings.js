'use client';

import { useEffect, useState } from 'react';
import { api } from './api';

const subscriptionPath = '/api/vote-push/subscription';

export async function disableVotePush() {
  if (!('serviceWorker' in navigator)) return;
  const registration = await navigator.serviceWorker.getRegistration('/');
  const subscription = await registration?.pushManager?.getSubscription();
  if (subscription) {
    await subscription.unsubscribe();
    await api(subscriptionPath, { method: 'DELETE', body: JSON.stringify({ endpoint: subscription.endpoint }) });
  }
}

function save(subscription) {
  return api(subscriptionPath, { method: 'PUT', body: JSON.stringify(subscription.toJSON()) });
}

export default function VotePushSettings({ onNotice }) {
  const [key, setKey] = useState('');
  const [active, setActive] = useState(false);
  const [busy, setBusy] = useState(true);
  const [hint, setHint] = useState('');

  useEffect(() => {
    async function load() {
      if (!('serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window)) {
        setHint('이 브라우저에서 푸시를 사용할 수 없습니다. iPhone은 홈 화면에 추가한 뒤 열어 주세요.');
        return;
      }
      const config = await api('/api/vote-push/config');
      if (!config.publicKey) { setHint('서버에서 일정 알림을 준비 중입니다.'); return; }
      await navigator.serviceWorker.register('/vote-push.js');
      const registration = await navigator.serviceWorker.ready;
      const subscription = await registration.pushManager.getSubscription();
      if (subscription && Notification.permission === 'granted') {
        const status = await api(subscriptionPath + '/status', { method: 'POST', body: JSON.stringify({ endpoint: subscription.endpoint }) });
        setActive(status.active);
      }
      setKey(config.publicKey);
    }
    load().catch(() => setHint('알림 설정을 불러오지 못했습니다.')).finally(() => setBusy(false));
  }, []);

  async function toggle() {
    setBusy(true);
    try {
      if (active) {
        await disableVotePush();
        setActive(false);
      } else {
        if (await Notification.requestPermission() !== 'granted') {
          onNotice('기기의 사이트 설정에서 알림을 허용해 주세요.');
          return;
        }
        const manager = (await navigator.serviceWorker.ready).pushManager;
        const applicationServerKey = Uint8Array.from(atob(key.replace(/-/g, '+').replace(/_/g, '/')), c => c.charCodeAt(0));
        const subscription = await manager.getSubscription() || await manager.subscribe({ userVisibleOnly: true, applicationServerKey });
        await save(subscription);
        setActive(true);
      }
      onNotice(active ? '일정 알림을 껐습니다.' : '새 일정 알림을 받습니다.');
    } catch { onNotice('알림 설정에 실패했습니다. 다시 시도해 주세요.'); }
    finally { setBusy(false); }
  }

  return hint ? <p className="help">{hint}</p> :
    <button type="button" className="text-button" disabled={busy || !key} aria-pressed={active} onClick={toggle}>
      {active ? '이 기기의 새 일정 알림 끄기' : '가입한 모임의 새 일정 알림 받기'}
    </button>;
}
