'use client';

import { useCallback, useEffect, useState } from 'react';
import { api } from './api';

const options = [['ATTENDING', '참석'], ['ABSENT', '불참석'], ['UNDECIDED', '보류']];

function suggestedTitle(date, location) {
  if (!date || !location.trim()) return '';
  const day = new Date(`${date}T12:00:00`);
  return `${date.slice(2).replaceAll('-', '.')}.(${['일', '월', '화', '수', '목', '금', '토'][day.getDay()]}) ${location.trim()} 운동`;
}

function localDateTime(value) {
  const date = new Date(value);
  const pad = number => String(number).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function isFutureDeadline(timestamp) {
  return Number.isFinite(timestamp) && timestamp > Date.now();
}

export default function AttendanceVotes({ clubId, isManager, onNotice }) {
  const [votes, setVotes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [pendingChoice, setPendingChoice] = useState(null);
  const [open, setOpen] = useState(false);
  const [location, setLocation] = useState('');
  const [date, setDate] = useState('');
  const [title, setTitle] = useState('');
  const [edited, setEdited] = useState(false);
  const [deadline, setDeadline] = useState('');
  const [editingVote, setEditingVote] = useState(null);
  const [newDeadline, setNewDeadline] = useState('');
  const [error, setError] = useState('');
  const [now, setNow] = useState(0);
  const path = `/api/clubs/${clubId}/attendance-votes`;
  const load = useCallback(async () => {
    try { setVotes(await api(path)); setError(''); }
    catch (failure) { setError(failure.message); }
    finally { setLoading(false); }
  }, [path]);
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 10000);
    const initial = setTimeout(() => { setNow(Date.now()); load(); }, 0);
    return () => { clearTimeout(initial); clearInterval(timer); };
  }, [load]);

  async function create(event) {
    event.preventDefault();
    if (!date || !location.trim() || !title.trim() || !deadline || new Date(deadline).getTime() < Date.now() + 4 * 3600 * 1000) {
      setError('장소, 운동 날짜, 제목과 4시간 이후의 마감 시각을 확인해 주세요.'); return;
    }
    setBusy(true); setError('');
    try {
      await api(path, { method: 'POST', body: JSON.stringify({ title, location, exerciseDate: date, closesAt: new Date(deadline).toISOString() }) });
      setOpen(false); setTitle(''); setDate(''); setLocation(''); setDeadline(''); setEdited(false);
      await load(); onNotice('참석 투표를 만들었습니다.');
    } catch (failure) { setError(failure.message); } finally { setBusy(false); }
  }

  async function respond(vote, choice) {
    setBusy(true); setPendingChoice({ voteId: vote.id, choice });
    try { await api(`${path}/${vote.id}/response`, { method: 'PUT', body: JSON.stringify({ choice }) }); await load(); onNotice('참석 여부를 저장했습니다.'); }
    catch (failure) { await load(); setError(failure.message); }
    finally { setBusy(false); setPendingChoice(null); }
  }

  async function saveDeadline(event, voteId) {
    event.preventDefault();
    const timestamp = new Date(newDeadline).getTime();
    if (!isFutureDeadline(timestamp)) { setError('마감 시각을 현재보다 이후로 지정해 주세요.'); return; }
    setBusy(true); setError('');
    try {
      await api(`${path}/${voteId}/deadline`, { method: 'PUT', body: JSON.stringify({ closesAt: new Date(timestamp).toISOString() }) });
      setEditingVote(null); await load(); onNotice('투표 마감 시간을 수정했습니다.');
    } catch (failure) { setError(failure.message); }
    finally { setBusy(false); }
  }

  return <section aria-label="운동 일정 참석 투표">
    <div className="section-heading"><div><div className="eyebrow blue">ATTENDANCE VOTE</div><h2>운동 일정 투표</h2></div>{isManager && <button className="text-button" onClick={() => setOpen(value => !value)}>{open ? '닫기' : '＋ 투표 만들기'}</button>}</div>
    <p className="help">투표 마감 전 푸시 알림은 아직 제공되지 않습니다.</p>
    {open && <form className="attendance-form" onSubmit={create}>
      <label>운동 날짜<input type="date" required value={date} onChange={e => { setDate(e.target.value); if (!edited) setTitle(suggestedTitle(e.target.value, location)); }} /></label>
      <label>장소<input required maxLength={100} value={location} onChange={e => { setLocation(e.target.value); if (!edited) setTitle(suggestedTitle(date, e.target.value)); }} /></label>
      <label>제목<input required maxLength={150} value={title} onChange={e => { setTitle(e.target.value); setEdited(true); }} /></label>
      <label>투표 마감 날짜와 시간<input type="datetime-local" required value={deadline} onChange={e => setDeadline(e.target.value)} /></label>
      <p className="help">마감 시각은 현재부터 4시간 이후로 지정해 주세요.</p><button className="primary" disabled={busy}>투표 만들기</button>
    </form>}
    {error && <p className="error" role="alert">{error}</p>}
    {loading ? <p className="help">투표를 불러오는 중입니다.</p> : !votes.length ? <div className="empty">등록된 운동 일정 투표가 없습니다.</div> : <div className="stack compact">{votes.map(vote => {
      const closed = now >= new Date(vote.closesAt).getTime();
      const selectedChoice = pendingChoice?.voteId === vote.id ? pendingChoice.choice : vote.myChoice;
      return <article className="attendance-vote" key={vote.id}><strong>{vote.title}</strong><p className="help">{vote.exerciseDate} · {vote.location}<br />마감 {new Date(vote.closesAt).toLocaleString('ko-KR')} {closed && '· 마감됨'}</p>
        {isManager && (editingVote === vote.id ? <form className="attendance-deadline-form" onSubmit={event => saveDeadline(event, vote.id)}><label>마감 날짜와 시간<input type="datetime-local" required value={newDeadline} onChange={event => setNewDeadline(event.target.value)} /></label>{closed && <p className="help">미래 시각으로 변경하면 투표가 다시 열립니다.</p>}<div className="button-row"><button type="button" className="secondary" disabled={busy} onClick={() => setEditingVote(null)}>취소</button><button type="submit" className="primary" disabled={busy}>저장</button></div></form> : <button type="button" className="text-button" disabled={busy} onClick={() => { setEditingVote(vote.id); setNewDeadline(localDateTime(vote.closesAt)); setError(''); }}>마감 시간 수정</button>)}
        <fieldset disabled={busy || closed}><legend>참석 여부</legend><div className="attendance-options">{options.map(([value, label]) => <label className={`attendance-option attendance-option--${value.toLowerCase()}${selectedChoice === value ? ' selected' : ''}`} key={value}><input type="radio" name={`vote-${vote.id}`} checked={selectedChoice === value} onChange={() => respond(vote, value)} />{label}</label>)}</div></fieldset>
        {options.map(([value, label]) => { const people = vote.responses.filter(person => person.choice === value); return <p className="help" key={value}>{label} {people.length}명{people.length > 0 && ` · ${people.map(person => person.name).join(', ')}`}</p>; })}
      </article>;
    })}</div>}
  </section>;
}
