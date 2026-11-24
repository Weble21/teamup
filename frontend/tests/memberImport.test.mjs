import test from 'node:test';
import assert from 'node:assert/strict';
import { buildImportRows, detectImportColumns, importMemberPayload, parseImportRating } from '../app/memberImport.mjs';

const basketball = 'BASKETBALL';
const lol = 'LEAGUE_OF_LEGENDS';
const mapping = { name: '0', position: '1', rating: '2' };
const selectAll = rows => rows.map(row => ({ ...row, selected: true }));

test('제목과 열 순서에 따라 이름·포지션·능력치를 같은 행에서 읽는다', () => {
  const source = [['능력치 (1~10)', '별도 메모', '포지션', '이름'], ['10', '메모', ' PG ', ' Luka Dončić '], ['5.0', '', 'SG', 'Chris Mañon']];
  const columns = detectImportColumns(source);
  assert.deepEqual(columns, { name: '3', position: '2', rating: '0' });
  const rows = buildImportRows(source, columns, true, basketball);
  assert.equal(rows[0].sourceRow, 2);
  assert.equal(rows[0].selected, false);
  assert.deepEqual(importMemberPayload(selectAll(rows), basketball), [
    { name: 'Luka Dončić', position: 'PG', rating: 100, age: null, notes: null },
    { name: 'Chris Mañon', position: 'SG', rating: 50, age: null, notes: null },
  ]);
});

test('제목 없는 파일의 첫 회원을 버리지 않고 수동 열 지정을 적용한다', () => {
  const rows = buildImportRows([['A', 7, 'C'], ['B', 1, 'PG']], { name: '0', position: '2', rating: '1' }, false, basketball);
  assert.equal(rows[0].sourceRow, 1);
  assert.deepEqual(importMemberPayload(selectAll(rows), basketball).map(row => row.rating), [70, 10]);
});

test('빈 행은 제외하고 일부 값이 없는 행은 원본 행 번호와 함께 남긴다', () => {
  const rows = buildImportRows([['이름', '포지션', '능력치'], ['', '', ''], ['A', 'C', '0'], ['', 'PG', '5']], mapping, true, basketball);
  assert.deepEqual(rows.map(row => row.sourceRow), [3, 4]);
  assert.equal(rows[0].rating, '0');
  assert.equal(rows[1].name, '');
  assert.throws(() => importMemberPayload(selectAll(rows), basketball), /3행/);
  assert.throws(() => importMemberPayload([{ ...rows[1], selected: true }], basketball), /4행/);
});

test('1~10 범위 밖이나 소수·빈 값·잘못된 값을 점수로 추정하지 않는다', () => {
  for (const value of [null, '', ' ', '0', '-1', '11', '100', '7.5', '10점', '1e1', '0xA', 'NaN', 'Infinity']) {
    const rows = buildImportRows([['A', 'C', value]], mapping, false, basketball);
    assert.throws(() => importMemberPayload(selectAll(rows), basketball));
  }
  assert.equal(parseImportRating('7.00', basketball), '7');
  assert.equal(parseImportRating('100', basketball), '100');
});

test('롤은 실제 티어 이름 또는 숫자로 입력하고 다른 종목은 티어를 받지 않는다', () => {
  const tiers = ['아이언', '브론즈', '실버', '골드', '플래티넘', '에메랄드', '다이아몬드', '마스터', '그랜드마스터', '챌린저'];
  const source = tiers.map((tier, index) => [`Player ${index}`, 'MID', tier]);
  const rows = buildImportRows(source, mapping, false, lol);
  assert.deepEqual(importMemberPayload(selectAll(rows), lol).map(row => row.rating), [10,20,30,40,50,60,70,80,90,100]);
  assert.throws(() => importMemberPayload(selectAll(buildImportRows([['A','C','골드']], mapping, false, basketball)), basketball));
  assert.throws(() => importMemberPayload(selectAll(buildImportRows([['A','MID','알 수 없음']], mapping, false, lol)), lol));
});

test('중복 열과 존재하지 않는 열 지정을 거부한다', () => {
  const source = [['A', 'C', '5']];
  assert.throws(() => buildImportRows(source, {name:'0',position:'0',rating:'2'}, false, basketball));
  assert.throws(() => buildImportRows(source, {name:'9',position:'1',rating:'2'}, false, basketball));
});

test('이름만 있는 파일도 직접 입력으로 채운 뒤 선택한 회원만 등록한다', () => {
  const columns = detectImportColumns([['이름'], ['A'], ['B']]);
  assert.deepEqual(columns, {name:'0',position:'',rating:''});
  const rows = buildImportRows([['이름'], ['A'], ['B']], columns, true, basketball);
  assert.equal(rows[0].position, ''); assert.equal(rows[0].rating, '');
  assert.throws(() => importMemberPayload(rows, basketball));
  assert.throws(() => importMemberPayload(selectAll(rows), basketball));
  rows[0] = {...rows[0],selected:true,position:' PG ',rating:'7'};
  assert.deepEqual(importMemberPayload(rows, basketball), [{name:'A',position:'PG',rating:70,age:null,notes:null}]);
});

test('100명 제한을 초과하면 조용히 자르지 않고 알린다', () => {
  const source = Array.from({length:100}, (_, i) => ['Name '+i, 'C', 5]);
  assert.equal(importMemberPayload(selectAll(buildImportRows(source, mapping, false, basketball)), basketball).length, 100);
  assert.throws(() => buildImportRows([...source, ['Overflow','PG',6]], mapping, false, basketball), /100명/);
});
