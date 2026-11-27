self.addEventListener('push', event => {
  const { clubId, voteId } = event.data?.json() ?? {};
  if (![clubId, voteId].every(id => Number.isSafeInteger(id) && id > 0)) return;
  event.waitUntil(self.registration.showNotification('TEAM UP', {
    body: '일정이 등록되었습니다! 확인해주세요.',
    icon: '/teamup-logo.png',
    tag: `attendance-${clubId}-${voteId}`,
    data: { clubId, voteId },
  }));
});

self.addEventListener('notificationclick', event => {
  event.notification.close();
  const { clubId, voteId } = event.notification.data;
  event.waitUntil(self.clients.openWindow('/?' + new URLSearchParams({ club: clubId, vote: voteId })));
});
