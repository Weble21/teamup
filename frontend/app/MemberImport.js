'use client';

import { useState } from 'react';

export default function MemberImport({ onClose, onSave, busy, saveError }) {
  const [file, setFile] = useState(null);
  const [result, setResult] = useState(null);
  const [column, setColumn] = useState('0');
  const [skipHeader, setSkipHeader] = useState(true);
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  function setCandidates(texts) {
    setRows(texts.filter(Boolean).slice(0, 100).map(name => ({ name, selected: false, position: '', rating: '' })));
  }
  async function extract() {
    if (!file || !file.name.toLowerCase().endsWith('.xlsx')) return setError('Excel(.xlsx) 파일을 선택해 주세요.');
    setLoading(true); setError(''); setResult(null); setRows([]);
    try {
      const form = new FormData(); form.append('file', file);
      const response = await fetch('/member-import', { method: 'POST', body: form });
      const data = await response.json();
      if (!response.ok) throw new Error(data.error);
      setResult(data);
    } catch (e) { setError(e.message || '파일을 처리하지 못했습니다.'); }
    finally { setLoading(false); }
  }
  function changeRow(index, changes) {
    setRows(old => old.map((row, i) => i === index ? { ...row, ...changes } : row));
  }
  function submit() {
    const selected = rows.filter(row => row.selected);
    if (!selected.length) return setError('등록할 이름을 선택해 주세요.');
    if (selected.some(row => !row.name.trim() || row.name.trim().length > 100 || !row.position.trim() || row.position.trim().length > 50 || !Number.isInteger(Number(row.rating)) || Number(row.rating) < 1 || Number(row.rating) > 10)) {
      return setError('선택한 회원의 이름, 포지션과 1~10 능력치를 모두 확인해 주세요.');
    }
    setError('');
    onSave(selected.map(row => ({ name: row.name.trim(), position: row.position.trim(), rating: Number(row.rating) * 10, age: null, notes: null })));
  }
  const columns = result?.kind === 'xlsx' ? Math.max(0, ...result.rows.map(row => row.length)) : 0;
  return <div className="overlay" onClick={onClose}><section className="modal import-modal" role="dialog" aria-modal="true" aria-labelledby="import-title" onClick={e => e.stopPropagation()}>
    <div className="modal-heading"><div><div className="eyebrow blue">IMPORT MEMBERS</div><h2 id="import-title">Excel에서 가져오기</h2></div><button className="close" onClick={onClose} aria-label="닫기">×</button></div>
    <p className="help">Excel(.xlsx)의 첫 번째 시트에서 이름이 있는 열을 선택하세요.</p>
    <label>파일 선택<input type="file" accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" onChange={e => { setFile(e.target.files?.[0] || null); setResult(null); setRows([]); setError(''); }} /></label>
    <button className="secondary import-button" type="button" disabled={loading || busy} onClick={extract}>{loading ? 'Excel 읽는 중…' : '파일에서 읽기'}</button>
    {result?.kind === 'xlsx' && <><label>이름이 있는 열<select value={column} onChange={e => setColumn(e.target.value)}>{Array.from({ length: columns }, (_, i) => <option value={i} key={i}>{i + 1}번째 열</option>)}</select></label><label className="check-line"><input type="checkbox" checked={skipHeader} onChange={e => setSkipHeader(e.target.checked)} />첫 행은 제목</label><button className="secondary import-button" type="button" onClick={() => setCandidates(result.rows.slice(skipHeader ? 1 : 0).map(row => row[Number(column)]?.trim()))}>이 열 불러오기</button></>}
    {rows.length > 0 && <><p className="help">등록할 이름을 선택하고 수정한 뒤, 포지션과 능력치를 입력하세요. 최대 100명까지 표시합니다.</p><div className="import-rows">{rows.map((row, i) => <div className="import-row" key={i}><label className="check-line"><input type="checkbox" checked={row.selected} onChange={e => changeRow(i, { selected: e.target.checked })} />등록</label><label>이름 또는 별명<input maxLength={100} value={row.name} onChange={e => changeRow(i, { name: e.target.value })} /></label>{row.selected && <><label>포지션<input maxLength={50} value={row.position} onChange={e => changeRow(i, { position: e.target.value })} /></label><label>능력치<select value={row.rating} onChange={e => changeRow(i, { rating: e.target.value })}><option value="">선택</option>{Array.from({length: 10}, (_, n) => <option key={n+1} value={n+1}>{n+1}</option>)}</select></label></>}</div>)}</div><button className="primary" type="button" disabled={busy || loading} onClick={submit}>선택한 회원 등록</button></>}
    {(error || saveError) && <p className="error" role="alert">{error || saveError}</p>}
  </section></div>;
}
