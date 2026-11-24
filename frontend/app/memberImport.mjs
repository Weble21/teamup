import { isLol, lolTiers } from './ratings.mjs';

const text = value => value == null ? '' : String(value).trim();
const validRating = value => /^(?:[1-9]|10)$/.test(text(value));

export function detectImportColumns(rows) {
  const header = (rows[0] ?? []).map(value => text(value).toLowerCase().replace(/\s+/g, ''));
  const find = names => {
    const index = header.findIndex(value => names.includes(value));
    return index < 0 ? '' : String(index);
  };
  return {
    name: find(['이름', '이름또는별명', '이름/별명', '선수명', '회원명', 'name']) || '0',
    position: find(['포지션', 'position']),
    rating: find(['능력치', '능력치(1~10)', '능력치(1-10)', '점수', 'rating', '티어', 'tier']),
  };
}

export function parseImportRating(value, sportType) {
  const raw = text(value);
  if (isLol(sportType)) {
    const tier = lolTiers.indexOf(raw);
    if (tier >= 0) return String(tier + 1);
  }
  // Accept 5.0, but never infer a 1~100 scale or round fractions.
  return /^(?:[1-9]|10)(?:\.0+)?$/.test(raw) ? String(Number(raw)) : raw;
}

export function buildImportRows(source, columns, skipHeader, sportType) {
  const width = Math.max(0, ...source.map(row => row.length));
  const mapped = Object.values(columns).filter(value => value !== '');
  if (columns.name === '' || mapped.some(value => !/^\d+$/.test(String(value)) || Number(value) >= width)) {
    throw new Error('가져올 열을 확인해 주세요.');
  }
  if (new Set(mapped.map(Number)).size !== mapped.length) throw new Error('이름, 포지션, 능력치는 서로 다른 열을 선택해 주세요.');
  const read = (row, column) => column === '' ? '' : text(row[Number(column)]);
  const candidates = source.slice(skipHeader ? 1 : 0).map((row, index) => ({
    name: read(row, columns.name),
    position: read(row, columns.position),
    rating: parseImportRating(read(row, columns.rating), sportType),
    selected: false,
    sourceRow: index + (skipHeader ? 2 : 1),
  })).filter(row => row.name || row.position || row.rating);
  if (candidates.length > 100) throw new Error('한 번에 100명까지 등록할 수 있습니다. 파일을 나누어 주세요.');
  if (!candidates.length) throw new Error('선택한 열에 가져올 회원 정보가 없습니다.');
  return candidates;
}

export function importMemberPayload(rows, sportType) {
  const selected = rows.filter(row => row.selected);
  if (!selected.length) throw new Error('등록할 회원을 선택해 주세요.');
  if (selected.length > 100) throw new Error('한 번에 100명까지 등록할 수 있습니다.');
  const invalid = selected.find(row => !text(row.name) || text(row.name).length > 100
    || !text(row.position) || text(row.position).length > 50 || !validRating(row.rating));
  if (invalid) throw new Error(`Excel ${invalid.sourceRow}행의 이름, 포지션과 ${isLol(sportType) ? '티어' : '1~10 능력치'}를 확인해 주세요.`);
  return selected.map(row => ({ name: text(row.name), position: text(row.position), rating: Number(row.rating) * 10, age: null, notes: null }));
}
