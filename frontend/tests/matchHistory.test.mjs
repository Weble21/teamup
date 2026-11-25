import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import { createRequire } from 'node:module';
import vm from 'node:vm';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { pendingAttendanceVotes } from '../app/attendanceVoteOrder.mjs';

const require = createRequire(import.meta.url);
const { loadBindings, transform } = require('next/dist/build/swc');
await loadBindings();
async function component(name, dependencies = {}) {
  const filename = new URL(`../app/${name}.js`, import.meta.url);
  const compiled = await transform(await fs.readFile(filename, 'utf8'), {
    filename: filename.pathname,
    jsc: { parser: { syntax: 'ecmascript', jsx: true }, transform: { react: { runtime: 'automatic' } } },
    module: { type: 'commonjs' },
  });
  const evaluated = { exports: {} };
  vm.runInNewContext(compiled.code, { module: evaluated, exports: evaluated.exports, require: name => dependencies[name] ?? require(name) });
  return evaluated.exports.default;
}
const ExerciseScheduleCard = await component('ExerciseScheduleCard');
const MatchHistoryCard = await component('MatchHistoryCard', { './ExerciseScheduleCard': { default: ExerciseScheduleCard, __esModule: true } });
const schedule = { id: 9, title: '26.11.27.(금) 체육관 운동', exerciseDate: '2026-11-27', location: '체육관', closesAt: '2026-11-25T01:00:00Z' };

test('팀 확정된 일정만 투표 목록에서 제외하고 마감된 미확정 일정은 남긴다', () => {
  const votes = [
    { ...schedule, id: 1, matchId: 50 },
    { ...schedule, id: 2, closed: true, matchId: null },
    { ...schedule, id: 3, closesAt: '2026-11-26T01:00:00Z', matchId: null },
  ];
  assert.deepEqual(pendingAttendanceVotes(votes, Date.parse('2026-11-25T02:00:00Z')).map(v => v.id), [3, 2]);
  assert.equal(votes.length, 3);
  assert.deepEqual(pendingAttendanceVotes([votes[0]], Date.now()), []);
});

test('일정에서 확정한 기록에는 제목 날짜 장소 마감과 경기 기록 버튼을 표시한다', () => {
  const html = renderToStaticMarkup(React.createElement(MatchHistoryCard, {
    match: { id: 50, attendanceVote: schedule }, onOpenMatch() {}, onOpenSchedule() {},
  }));
  for (const text of [schedule.title, schedule.exerciseDate, schedule.location, '마감', '경기 기록 보기']) assert.ok(html.includes(text));
  assert.match(html, /attendance-list-item/);
  assert.doesNotMatch(html, /팀 편성 기록|팀 나누기|disabled/);
  assert.equal((html.match(/<button\b/g) ?? []).length, 2);
});

for (const attendanceVote of [null, undefined]) {
  test(`빠른 편성 기록은 기존 카드와 참석자·팀 수를 유지한다 (${attendanceVote})`, () => {
    const html = renderToStaticMarkup(React.createElement(MatchHistoryCard, {
      match: { id: 'quick', date: '2026-11-25T01:00:00Z', sport: '농구', teams: [[{ id: 1 }], [{ id: 2 }]], attendanceVote },
      onOpenMatch() {}, onOpenSchedule() {},
    }));
    assert.match(html, /history-card/);
    assert.match(html, /팀 편성 기록/);
    assert.match(html, /참석 2명 · 2개 팀/);
    assert.doesNotMatch(html, /attendance-list-item|마감|경기 기록 보기/);
  });
}
