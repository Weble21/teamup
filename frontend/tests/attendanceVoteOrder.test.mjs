import test from 'node:test';
import assert from 'node:assert/strict';
import { sortAttendanceVotes } from '../app/attendanceVoteOrder.mjs';

test('진행 중인 투표는 마감이 가까운 순이고 종료된 투표는 그 아래 최근 마감 순이다', () => {
  const now = Date.parse('2026-11-25T10:00:00Z');
  const votes = [
    { id: 1, closesAt: '2026-11-25T12:00:00Z', closed: false },
    { id: 2, closesAt: '2026-11-25T08:00:00Z', closed: true },
    { id: 3, closesAt: '2026-11-25T11:00:00Z', closed: false },
    { id: 4, closesAt: '2026-11-25T09:00:00Z', closed: true },
  ];
  assert.deepEqual(sortAttendanceVotes(votes, now).map(vote => vote.id), [3, 1, 4, 2]);
  assert.deepEqual(votes.map(vote => vote.id), [1, 2, 3, 4]);
});

test('마감 시각이 지나면 서버 응답 갱신 전에도 종료된 투표로 정렬한다', () => {
  const votes = [
    { id: 1, closesAt: '2026-11-25T10:00:00Z', closed: false },
    { id: 2, closesAt: '2026-11-25T11:00:00Z', closed: false },
  ];
  assert.deepEqual(sortAttendanceVotes(votes, Date.parse('2026-11-25T10:00:00Z')).map(vote => vote.id), [2, 1]);
});
