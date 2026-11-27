import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { runInNewContext } from 'node:vm';

test('새 일정 알림 문구와 클릭한 투표의 상세 주소를 유지한다', async () => {
  const handlers = {};
  let notification;
  let opened;
  const self = {
    addEventListener: (type, handler) => { handlers[type] = handler; },
    registration: { showNotification: async (title, options) => { notification = { title, ...options }; } },
    clients: { openWindow: async url => { opened = url; } },
  };
  runInNewContext(readFileSync(new URL('../public/vote-push.js', import.meta.url), 'utf8'), { self, URLSearchParams });
  let pending;
  const waitUntil = promise => { pending = promise; };
  handlers.push({ data: { json: () => ({ clubId: 12, voteId: 34 }) }, waitUntil });
  await pending;
  assert.equal(notification.body, '일정이 등록되었습니다! 확인해주세요.');
  assert.equal(notification.tag, 'attendance-12-34');
  handlers.notificationclick({ notification: { ...notification, close() {} }, waitUntil });
  await pending;
  assert.equal(opened, '/?club=12&vote=34');
});
