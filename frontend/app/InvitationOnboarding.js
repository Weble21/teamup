'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { api, sports } from './api';
import RatingField from './RatingField';
import { invitationStep } from './invitationFlow.mjs';

export default function InvitationOnboarding({ token, user, onComplete, onCancel, onProfileSaved, onNotice, onLoginRequired }) {
  const [preview, setPreview] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [form, setForm] = useState({ name: user.name, position: '', rating: 50, age: '', notes: '' });
  const operation = useRef(false);
  const lastSport = useRef(null);
  const lifecycle = useRef(null);

  const refresh = useCallback(signal =>
    api('/api/invitations/preview', { method: 'POST', body: JSON.stringify({ token }), signal })
      .then(async result => {
        if (signal?.aborted) return;
        if (lastSport.current && lastSport.current !== result.sportType) {
          setForm({ name: user.name, position: '', rating: 50, age: '', notes: '' });
        }
        lastSport.current = result.sportType;
        setPreview(result);
        if (invitationStep(result) === 'joined') await onComplete({ clubId: result.clubId, alreadyJoined: true }, signal);
      })
      .catch(failure => {
        if (signal?.aborted) return;
        if (failure.status === 401) onLoginRequired();
        else { setPreview(null); setError(failure.message); }
      })
      .finally(() => { if (!signal?.aborted) setLoading(false); }),
  [token, user.name, onComplete, onLoginRequired]);
  useEffect(() => {
    const controller = new AbortController();
    lifecycle.current = controller;
    refresh(controller.signal);
    return () => controller.abort();
  }, [refresh]);

  async function recheck() {
    setLoading(true); setError('');
    await refresh(lifecycle.current?.signal);
  }

  async function saveProfile(event) {
    event.preventDefault();
    if (operation.current) return;
    const age = form.age === '' ? null : Number(form.age);
    if (!form.name.trim() || !form.position.trim() || !Number.isInteger(form.rating) || form.rating < 1 || form.rating > 100
      || (age !== null && (!Number.isInteger(age) || age < 0 || age > 130))) return setError('이름, 포지션, 능력치와 나이를 확인해 주세요.');
    operation.current = true; setBusy(true); setError('');
    const signal = lifecycle.current?.signal;
    try {
      const saved = await api(`/api/users/me/sport-profiles/${preview.sportType}`, { method: 'PUT', signal, body: JSON.stringify({
        name: form.name.trim(), position: form.position.trim(), rating: form.rating, age, notes: form.notes.trim() || null,
      }) });
      if (signal?.aborted) return;
      onProfileSaved(saved);
      onNotice('내 정보를 저장했습니다. 초대를 수락해 주세요.');
      await recheck();
    } catch (failure) { if (!signal?.aborted) { if (failure.status === 401) onLoginRequired(); else setError(failure.message); } }
    finally { operation.current = false; setBusy(false); }
  }

  async function accept() {
    if (operation.current) return;
    operation.current = true; setBusy(true); setError('');
    const signal = lifecycle.current?.signal;
    try {
      const result = await api('/api/invitations/join', { method: 'POST', signal, body: JSON.stringify({ token }) });
      if (signal?.aborted) return;
      await onComplete(result, signal);
    } catch (failure) {
      if (signal?.aborted) return;
      if (failure.status === 401) onLoginRequired();
      else if (failure.status === 409) await recheck();
      else { if (failure.status === 404) setPreview(null); setError(failure.message); }
    } finally { operation.current = false; setBusy(false); }
  }

  const step = preview && invitationStep(preview);
  return <section className="modal invitation-onboarding" aria-labelledby="invitation-title" aria-busy={loading || busy}>
    <div className="eyebrow blue">CLUB INVITATION</div>
    <h2 id="invitation-title">{preview?.clubName ?? '모임 초대'}</h2>
    {loading ? <p role="status">초대와 가입 정보를 확인하고 있습니다.</p> : step === 'profile' ? <>
      <p className="help">{sports[preview.sportType]} 내 정보를 먼저 등록해 주세요. 등록 후 초대 수락 화면으로 이어집니다.</p>
      <form onSubmit={saveProfile}>
        <label>이름 또는 별명 <b>*</b><input required maxLength={100} value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></label>
        <label>포지션 <b>*</b><input required maxLength={50} value={form.position} onChange={e => setForm({ ...form, position: e.target.value })} /></label>
        <RatingField sportType={preview.sportType} rating={form.rating} onChange={rating => setForm({ ...form, rating })} />
        <label>나이 <small>선택</small><input type="number" min="0" max="130" value={form.age} onChange={e => setForm({ ...form, age: e.target.value })} /></label>
        <label>추가 사항 <small>선택</small><textarea maxLength={1000} value={form.notes} onChange={e => setForm({ ...form, notes: e.target.value })} /></label>
        <button type="submit" className="primary" disabled={busy}>내 정보 저장 후 계속</button>
      </form>
    </> : step === 'accept' ? <>
      <p className="help">{sports[preview.sportType]} 내 정보가 등록되어 있습니다. 초대를 수락하면 저장된 정보로 이 모임의 선수로 등록됩니다.</p>
      <button type="button" className="primary" disabled={busy} onClick={accept}>초대 수락</button>
    </> : null}
    {error && <><p className="error" role="alert">{error}</p><button type="button" className="secondary" disabled={busy || loading} onClick={recheck}>다시 확인</button></>}
    <button type="button" className="text-button invitation-cancel" disabled={busy || loading} onClick={onCancel}>초대 닫기</button>
  </section>;
}
