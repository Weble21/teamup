import test from 'node:test';
import assert from 'node:assert/strict';
import { requestedClub, clubPageUrl } from '../app/clubNavigation.mjs';

test('새로고침 URL의 모임은 현재 계정에서 접근 가능한 목록과 대조한다', () => {
  const clubs = [{ id: 7 }, { id: 12 }];
  const url = clubPageUrl('http://localhost:3000/', 12);
  assert.equal(url, '/?club=12');
  assert.equal(requestedClub(new URL(url, 'http://localhost:3000').search, clubs), clubs[1]);
  for (const search of ['', '?club=999', '?club=undefined', '?club=12%20OR%201=1']) {
    assert.equal(requestedClub(search, clubs), null);
  }
});

test('다른 계정의 모임 URL을 그대로 열어도 화면 복원을 허용하지 않는다', () => {
  assert.equal(requestedClub('?club=12', [{ id: 7 }]), null);
});

test('목록으로 이동하면 모임 URL 상태를 제거하고 다른 파라미터는 보존한다', () => {
  assert.equal(clubPageUrl('http://localhost:3000/?club=12&guest=1&signedIn=1', null), '/?signedIn=1');
});

test('게스트 모임 복원은 문자열 ID와 게스트 모드를 유지한다', () => {
  const clubs = [{ id: 'guest-uuid' }];
  assert.equal(clubPageUrl('http://localhost:3000/', 'guest-uuid', true), '/?club=guest-uuid&guest=1');
  assert.equal(requestedClub('?club=guest-uuid&guest=1', clubs), clubs[0]);
  assert.equal(clubPageUrl('http://localhost:3000/?guest=1', 12), '/?club=12');
});
