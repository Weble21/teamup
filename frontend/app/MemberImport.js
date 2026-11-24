'use client';

import { useState } from 'react';
import { isLol, lolTiers } from './ratings.mjs';
import { buildImportRows, detectImportColumns, importMemberPayload } from './memberImport.mjs';

export default function MemberImport({ onClose, onSave, busy, saveError, sportType }) {
  const [file, setFile] = useState(null);
  const [result, setResult] = useState(null);
  const [mapping, setMapping] = useState({ name: '0', position: '', rating: '' });
  const [skipHeader, setSkipHeader] = useState(true);
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function extract() {
    if (!file || !file.name.toLowerCase().endswith('.xlsx')) return setError('Excel(.xlsx) 파일을 선택해 주세요.');
    setLoading(true); setError(''); setResult(null); setRows([]);
    try {
      const form = new FormData(); form.append('file', file);
      const response = await fetch('/member-import', { method: 'POST', body: form });
      const data = await response.json();
      if (!response.ok) throw new Error(data.error);
      setResult(data); setMapping(detectImportColumns(data.rows)); setSkipHeader(true);
    } catch (e) { setError(e.message || '파일을 처리하지 못했습니다.'); }
    finally { setLoading(false); }
  }
  function changeMapping(field, value) {
    setMapping(old => ({ ...old, [field]: value })); setRows([]); setError('');
  }
  function loadRows() {
    try { setRows(buildImportRows(result.rows, mapping, skipHeader, sportType)); setError(''); }
    catch (failure) { setRows([]); setError(failure.message); }
  }
  function changeRow(index, changes) {
    setRows(old => old.map((row, i) => i === index ? { ...row, ...changes } : row));
  }
  function submit() {
    let members;
    try { members = importMemberPayload(rows, sportType); }
    catch (failure) { return setError(failure.message); }
    setError(''); onSave(members);
  }
  const columns = result?.kind === 'xlsx' ? Math.max(0, ...result.rows.map(row => row.length)) : 0;
  const options = Array.from({ length: columns }, (_, index) => {
    const header = skipHeader ? String(result.rows[0]?.[index] ?? '').trim() : '';
    return <option value={index} key={index}>{index + 1}번째 열{header ? ` · ${header.slice(0, 30)}` : ''}</option>;
  });
  const ratingName = isLol(sportType) ? '티어' : '능력치';
  return <div className="overlay" onClick={onClose}><section className="modal import-modal" role="dialog" aria-modal="true" aria-labelledby="import-title" onClick={e => e.stopPropagation()}>
    <div className="modal-heading"><div><div className="eyebrow blue">IMPORT MEMBERS</div><h2 id="import-title">Excel에서 가져오기</h2></div><button className="close" disabled={busy} onClick={onClose} aria-label="닫기">×</button></div>
    <p className="help">Excel(.xlsx)의 첫 번째 시트에서 이름·포지션·{ratingName} 열을 선택하세요. 가져온 값은 등록 전에 수정할 수 있습니다.</p>
    <fieldset className="import-fields" disabled={busy || loading}>
      <label>파일 선택<input type="file" accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" onChange={e => { setFile(e.target.files?.[0] || null); setResult(null); setRows([]); setError(''); }} /></label>
      <button className="secondary import-button" type="button" onClick={extract}>{loading ? 'Excel 읽는 중…' : '파일에서 읽기'}</button>
      {result?.kind === 'xlsx' && <>
        <label className="check-line"><input type="checkbox" checked={skipHeader} onChange={e => { setSkipHeader(e.target.checked); setRows([]); setError(''); }} />첫 행은 제목</label>
        <label>이름이 있는 열<select value={mapping.name} onChange={e => changeMapping('name', e.target.value)}>{options}</select></label>
        <label>포지션이 있는 열<select value={mapping.position} onChange={e => changeMapping('position', e.target.value)}><option value="">직접 입력</option>{options}</select></label>
        <label>{ratingName}가 있는 열<select value={mapping.rating} onChange={e => changeMapping('rating', e.target.value)}><option value="">직접 입력</option>{options}</select></label>
        <p className="help">{isLol(sportType) ? '티어 이름(아이언~챌린저) 또는 1~10 정수를 지원합니다.' : '능력치는 1~10 정수를 사용하세요. 1~100 저장값은 지원하지 않습니다.'} 열이 없으면 직접 입력을 선택하세요.</p>
        <button className="secondary import-button" type="button" onClick={loadRows}>선택한 열로 회원 불러오기</button>
      </>}
      {rows.length > 0 && <>
        <p className="help">등록할 회원을 선택하고 이름·포지션·{ratingName}를 확인하세요. 빈 값이나 잘못된 값은 수정해야 등록할 수 있습니다.</p>
        <label className="check-line"><input type="checkbox" checked={rows.every(row => row.selected)} onChange={e => setRows(old => old.map(row => ({ ...row, selected: e.target.checked })))} />전체 선택 ({rows.filter(row => row.selected).length}/{rows.length}명)</label>
        <div className="import-rows">{rows.map((row, index) => <div className="import-row" key={row.sourceRow}>
          <label className="check-line"><input type="checkbox" checked={row.selected} onChange={e => changeRow(index, { selected: e.target.checked })} />Excel {row.sourceRow}행 등록</label>
          <label>이름 또는 별명<input maxLength={100} value={row.name} onChange={e => changeRow(index, { name: e.target.value })} /></label>
          <label>포지션<input maxLength={50} value={row.position} onChange={e => changeRow(index, { position: e.target.value })} /></label>
          <label>{ratingName}<select value={row.rating} onChange={e => changeRow(index, { rating: e.target.value })}>
            <option value="">선택</option>
            {row.rating && !/^(?:[1-9]|10)$/.test(row.rating) && <option value={row.rating}>원본 값: {row.rating} · 수정 필요</option>}
            {Array.from({ length: 10 }, (_, n) => <option key={n + 1} value={n + 1}>{isLol(sportType) ? lolTiers[n] : n + 1}</option>)}
          </select></label>
        </div>)}</div>
        <button className="primary" type="button" onClick={submit}>선택한 회원 등록</button>
      </>}
    </fieldset>
    {(error || saveError) && <p className="error" role="alert">{error || saveError}</p>}
  </section></div>;
}
