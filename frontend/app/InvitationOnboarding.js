'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { api, sports } from './api';
import RatingField from './RatingField';

export default function InvitationOnboarding({ token, user, onComplete, onCancel, onLoginRequired }) {
  const [preview, setPreview] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [selected, setSelected] = useState('');
  const [registering, setRegistering] = useState(false);
  const [form, setForm] = useState({ name: user.name, position: '', rating: 50, age: '', notes: '' });
  const operation = useRef(false);
  const lifecycle = useRef(null);

  const refresh = useCallback(async signal => {
    try {
      const result = await api('/api/invitations/preview', { method: 'POST', body: JSON.stringify({ token }), signal });
      if (signal?.aborted) return;
      setPreview(result);
      if (result.alreadyJoined) await onComplete({ clubId: result.clubId, alreadyJoined: true }, signal);
    } catch (failure) {
      if (signal?.aborted) return;
      if (failure.status === 401) onLoginRequired(); else { setPreview(null); setError(failure.message); }
    } finally { if (!signal?.aborted) setLoading(false); }
  }, [token, onComplete, onLoginRequired]);

  useEffect(() => {
    const controller = new AbortController(); lifecycle.current = controller;
    const timer = setTimeout(() => refresh(controller.signal), 0);
    return () => { clearTimeout(timer); controller.abort(); };
  }, [refresh]);

  async function join(payload) {
    if (operation.current) return;
    operation.current = true; setBusy(true); setError('');
    const signal = lifecycle.current?.signal;
    try {
      const result = await api('/api/invitations/join', { method: 'POST', signal, body: JSON.stringify({ token, ...payload }) });
      if (!signal?.aborted) await onComplete(result, signal);
    } catch (failure) {
      if (signal?.aborted) return;
      if (failure.status === 401) onLoginRequired();
      else if (failure.status === 409) { await refresh(signal); setSelected(''); setError('선택한 선수 정보를 사용할 수 없습니다. 명단을 다시 확인해 주세요.'); }
      else { if (failure.status === 404) setPreview(null); setError(failure.message); }
    } finally { operation.current = false; setBusy(false); }
  }

  function register(event) {
    event.preventDefault();
    const age = form.age === '' ? null : Number(form.age);
    if (!form.name.trim() || !form.position.trim() || !Number.isInteger(form.rating) || form.rating < 1 || form.rating > 100 ||
      (age !== null && (!Number.isInteger(age) || age < 0 || age > 130))) { setError('이름, 포지션, 능력치와 나이를 확인해 주세요.'); return; }
    join({ profile: { name: form.name.trim(), position: form.position.trim(), rating: form.rating, age, notes: form.notes.trim() || null } });
  }

  return <section className="modal invitation-onboarding" aria-labelledby="invitation-title" aria-busy={loading || busy}>
    <div className="eyebrow blue">CLUB INVITATION</div><h2 id="invitation-title">{preview?.clubName ?? '모임 초대'}</h2>
    {loading ? <p role="status">초대와 가입 정보를 확인하고 있습니다.</p> : preview && !preview.alreadyJoined && (registering ? <>
      <p className="help">{sports[preview.sportType]} 모임에서 사용할 내 정보를 등록해 주세요.</p>
      <form onSubmit={register}>
        <label>이름 또는 별명 <b>*</b><input required maxLength={100} value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></label>
        <label>포지션 <b>*</b><input required maxLength={50} value={form.position} onChange={e => setForm({ ...form, position: e.target.value })} /></label>
        <RatingField sportType={preview.sportType} rating={form.rating} onChange={rating => setForm({ ...form, rating })} />
        <label>나이 <small>선택</small><input type="number" min="0" max="130" value={form.age} onChange={e => setForm({ ...form, age: e.target.value })} /></label>
        <label>추가 사항 <small>선택</small><textarea maxLength={1000} value={form.notes} onChange={e => setForm({ ...form, notes: e.target.value })} /></label>
        <button type="submit" className="primary" disabled={busy}>등록하고 가입하기</button>
      </form><button type="button" className="text-button" disabled={busy} onClick={() => { setRegistering(false); setError(''); }}>명단으로 돌아가기</button>
    </> : <>
      <p className="help">먼저 선수 명단에서 본인을 확인해 주세요.</p>
      {preview.candidates.length ? <fieldset disabled={busy}><legend>기존 선수 명단</legend>{preview.candidates.map(person => <label className="select-card" key={person.id}><input type="radio" name="invitation-member" value={person.id} checked={selected === String(person.id)} onChange={e => setSelected(e.target.value)} /><strong>{person.name}</strong><small>{person.position}</small></label>)}</fieldset> : <p className="help">선택할 수 있는 기존 선수가 없습니다.</p>}
      <button type="button" className="primary" disabled={busy || !selected} onClick={() => join({ memberId: Number(selected) })}>선택하고 가입하기</button>
      <button type="button" className="secondary" disabled={busy} onClick={() => { setRegistering(true); setError(''); }}>명단에 없어요 · 내 정보 등록</button>
    </>)}
    {error && <><p className="error" role="alert">{error}</p><button type="button" className="secondary" disabled={busy || loading} onClick={() => { setLoading(true); setError(''); refresh(lifecycle.current?.signal); }}>다시 확인</button></>}
    <button type="button" className="text-button invitation-cancel" disabled={busy || loading} onClick={onCancel}>초대 닫기</button>
  </section>;
}
