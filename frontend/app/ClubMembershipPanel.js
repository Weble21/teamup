'use client';

import { useEffect, useRef, useState } from 'react';
import { api } from './api';

export default function ClubMembershipPanel({ clubId, userId, isManager, onChanged, onRemoved, onNotice }) {
  const [people, setPeople] = useState(null);
  const [action, setAction] = useState(null);
  const [confirmation, setConfirmation] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const running = useRef(false);
  const modal = useRef(null);

  useEffect(() => {
    if (!action) return;
    const previous = document.activeElement;
    const root = modal.current;
    root?.querySelector('input, button')?.focus();
    function trap(event) {
      if (event.key !== 'Tab') return;
      const controls = [...root.querySelectorAll('button:not(:disabled), input:not(:disabled)')];
      const first = controls[0], last = controls.at(-1);
      if (!first) { event.preventDefault(); return; }
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    }
    root?.addEventListener('keydown', trap);
    return () => { root?.removeEventListener('keydown', trap); if (previous?.isConnected) previous.focus(); };
  }, [action]);

  async function run(work) {
    if (running.current) return;
    running.current = true; setBusy(true); setError('');
    try { await work(); }
    catch (failure) { setError(failure.message); onNotice(failure.message); }
    finally { running.current = false; setBusy(false); }
  }

  function ask(next) { setAction(next); setConfirmation(''); setError(''); }
  function close() { if (!running.current) { setAction(null); setError(''); } }

  async function execute() {
    if (action?.type === 'delete' && confirmation !== '삭제') return;
    return run(async () => {
      const type = action.type;
      if (type === 'delete') {
        await api(`/api/clubs/${clubId}`, { method: 'DELETE', body: JSON.stringify({ confirmation }) });
      } else if (type === 'leave') {
        await api(`/api/clubs/${clubId}/leave`, { method: 'POST' });
      } else {
        await api(`/api/clubs/${clubId}/managers/${action.person.userId}`, {
          method: 'PUT', body: JSON.stringify({ manager: type !== 'revoke', transfer: type === 'transfer' }),
        });
      }
      setAction(null); setPeople(null);
      onNotice(type === 'delete' ? '모임을 삭제했습니다.' : type === 'leave' ? '모임에서 탈퇴했습니다.' : '모임장 권한을 변경했습니다.');
      if (type === 'delete' || type === 'leave') onRemoved(clubId);
      else await onChanged();
    });
  }

  const title = action?.type === 'delete' ? '정말 삭제하시겠습니까?'
    : action?.type === 'leave' ? '모임에서 탈퇴하시겠습니까?'
    : action?.type === 'transfer' ? '모임장 권한을 위임하시겠습니까?'
    : action?.type === 'revoke' ? '모임장 권한을 해제하시겠습니까?' : '모임장을 추가하시겠습니까?';

  return <section className="membership-panel" aria-label="모임 가입 및 권한 관리">
    {isManager && <button type="button" className="secondary" disabled={busy} onClick={() => people ? setPeople(null) : run(async () => setPeople(await api(`/api/clubs/${clubId}/memberships`)))}>{people ? '모임장 관리 닫기' : '모임장 관리'}</button>}
    {isManager && people && <div className="membership-people"><p className="help">모든 모임장은 동일한 권한을 가집니다. 위임하면 내 모임장 권한은 해제됩니다.</p>{people.map(person => <article className="membership-person" key={person.userId}>
      <strong>{person.name}{person.userId === userId ? ' (나)' : ''} <small>{person.manager ? '모임장' : '모임원'}</small></strong>
      <div className="membership-actions">
        <button type="button" className="text-button" disabled={busy} onClick={() => ask({ type: person.manager ? 'revoke' : 'grant', person })}>{person.manager ? '권한 해제' : '모임장 추가'}</button>
        {person.userId !== userId && <button type="button" className="text-button" disabled={busy} onClick={() => ask({ type: 'transfer', person })}>권한 위임</button>}
      </div>
    </article>)}</div>}
    <div className="membership-actions">
      <button type="button" className="text-button danger-text" disabled={busy} onClick={() => ask({ type: 'leave' })}>모임 탈퇴</button>
      {isManager && <button type="button" className="text-button danger-text" disabled={busy} onClick={() => ask({ type: 'delete' })}>모임 삭제</button>}
    </div>
    {error && !action && <p className="error" role="alert">{error}</p>}
    {action && <div className="overlay" onClick={close} onKeyDown={event => { if (event.key === 'Escape') close(); }}><section ref={modal} className="modal membership-modal" role="dialog" aria-modal="true" aria-labelledby="membership-title" aria-describedby="membership-description" onClick={event => event.stopPropagation()}>
      <div className="modal-heading"><h2 id="membership-title">{title}</h2><button type="button" className="close" disabled={busy} aria-label="닫기" onClick={close}>×</button></div>
      <p id="membership-description">{action.type === 'delete'
        ? '모임을 삭제하면 모든 회원이 이 모임에 접근할 수 없으며, 선수 명단·운동 일정·투표·댓글·경기 기록이 영구 삭제됩니다. 삭제된 데이터는 복구할 수 없습니다.'
        : action.type === 'leave' ? '탈퇴하면 이 모임에 접근하거나 새 활동에 참여할 수 없습니다. 기존 기록은 유지되며, 초대 링크를 통해 다시 가입할 수 있습니다. 마지막 모임장은 먼저 다른 회원에게 권한을 넘겨야 합니다.'
          : action.type === 'transfer' ? `${action.person.name}님에게 모임장 권한을 넘기고 내 권한은 해제합니다. 모임원으로는 계속 참여합니다.`
            : action.type === 'revoke' ? `${action.person.name}님의 모임장 권한을 해제합니다. 모임원으로는 계속 참여합니다. 마지막 모임장의 권한은 해제할 수 없습니다.`
              : `${action.person.name}님도 모임장 지정·해제와 모임 삭제를 포함한 모든 모임장 기능을 사용할 수 있게 됩니다.`}</p>
      {action.type === 'delete' && <label>계속하려면 아래에 <b>삭제</b>를 입력해 주세요.<input value={confirmation} onChange={event => setConfirmation(event.target.value)} autoComplete="off" disabled={busy} /></label>}
      {error && <p className="error" role="alert">{error}</p>}
      <div className="button-row"><button type="button" className="secondary" disabled={busy} onClick={close}>취소</button><button type="button" className={`primary ${action.type === 'delete' ? 'danger-action' : ''}`} disabled={busy || (action.type === 'delete' && confirmation !== '삭제')} onClick={execute}>{busy ? '처리 중…' : action.type === 'delete' ? '모임 영구 삭제' : action.type === 'leave' ? '탈퇴' : '확인'}</button></div>
    </section></div>}
  </section>;
}
