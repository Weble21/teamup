import test from 'node:test';
import assert from 'node:assert/strict';
import { formatRating, isTierRating, lolTiers } from '../app/ratings.mjs';

test('롤의 10~100 저장값은 순서대로 아이언~챌린저로 표시한다', () => {
  assert.equal(lolTiers.length, 10);
  const expected = ['아이언', '브론즈', '실버', '골드', '플래티넘', '에메랄드', '다이아몬드', '마스터', '그랜드마스터', '챌린저'];
  expected.forEach((tier, index) => {
    assert.equal(formatRating((index + 1) * 10, 'LEAGUE_OF_LEGENDS', ' / 10'), tier);
    assert.equal(isTierRating((index + 1) * 10), true);
  });
});

test('기존 중간값과 하한값은 가까운 티어로 환산하며 입력 데이터는 보존한다', () => {
  const member = { rating: 55 };
  assert.equal(formatRating(member.rating, 'LEAGUE_OF_LEGENDS'), '에메랄드 (환산)');
  assert.equal(member.rating, 55);
  assert.equal(formatRating(54, 'LEAGUE_OF_LEGENDS'), '플래티넘 (환산)');
  assert.equal(formatRating(1, 'LEAGUE_OF_LEGENDS'), '아이언 (환산)');
  assert.equal(isTierRating(55), false);
});

test('다른 종목의 숫자 점수와 기존 소수점 표시를 유지한다', () => {
  for (const sport of ['BASKETBALL', 'FUTSAL', 'FOOTBALL', 'VOLLEYBALL', 'BASEBALL', 'OTHER']) {
    assert.equal(formatRating(55, sport), '5.5점');
    assert.equal(formatRating(100, sport, ' / 10'), '10 / 10');
  }
});

test('미등록 값이나 범위 밖의 값에 티어를 부여하지 않는다', () => {
  for (const value of [null, undefined, NaN, 0, -1, 101]) {
    assert.equal(formatRating(value, 'LEAGUE_OF_LEGENDS'), '미등록');
    assert.equal(isTierRating(value), false);
  }
});
