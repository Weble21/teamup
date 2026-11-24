import test from 'node:test';
import assert from 'node:assert/strict';
import { invitationStep } from '../app/invitationFlow.mjs';

test('기존 가입자는 종목별 내 정보가 없어도 정보 등록이나 수락을 다시 요구하지 않는다', () => {
  assert.equal(invitationStep({ alreadyJoined: true, profileRegistered: false }), 'joined');
  assert.equal(invitationStep({ alreadyJoined: true, profileRegistered: true }), 'joined');
});

test('미가입자는 해당 종목 정보가 없으면 등록하고 있으면 수락 화면을 표시한다', () => {
  assert.equal(invitationStep({ alreadyJoined: false, profileRegistered: false }), 'profile');
  assert.equal(invitationStep({ alreadyJoined: false, profileRegistered: true }), 'accept');
});
