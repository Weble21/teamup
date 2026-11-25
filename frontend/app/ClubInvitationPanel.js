'use client';

import { useEffect, useId, useRef, useState } from 'react';
import { api } from './api';

function ConfirmInvitationDialog({ method, onCancel, onConfirm }) {
  const dialog = useRef(null);
  const titleId = useId();
  const descriptionId = useId();
  const isReissue = method === 'POST';

  useEffect(() => {
    const element = dialog.current;
    element.showModal();
    return () => element.close();
  }, []);

  return <dialog ref={dialog} className="modal alert-dialog" aria-labelledby={titleId} aria-describedby={descriptionId} onCancel={event => { event.preventDefault(); onCancel(); }}>
    <h2 id={titleId}>{isReissue ? '새 링크를 발급할까요?' : '링크를 비활성화할까요?'}</h2>
    <p id={descriptionId}>{isReissue ? '새 링크를 발급하면 이전 링크로는 모임에 가입할 수 없습니다.' : '링크를 비활성화하면 해당 링크로는 모임에 가입할 수 없습니다.'}</p>
    <div className="button-row"><button type="button" className="secondary" autoFocus onClick={onCancel}>취소</button><button type="button" className="primary" onClick={onConfirm}>{isReissue ? '새 링크 발급' : '비활성화'}</button></div>
  </dialog>;
}

export default function ClubInvitationPanel({ clubId, onNotice, onCopied }) {
  const [invitation, setInvitation] = useState(null);
  const [expanded, setExpanded] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [confirmMethod, setConfirmMethod] = useState(null);
  const operation = useRef(false);
  const contentId = useId();
  const path = `/api/clubs/${clubId}/invitation`;

  useEffect(() => {
    const controller = new AbortController();
    api(path, { signal: controller.signal })
      .then(result => { if (!controller.signal.aborted) setInvitation(result); })
      .catch(failure => { if (!controller.signal.aborted) setError(failure.message); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [path]);

  async function changeInvitation(method) {
    if (operation.current) return;
    operation.current = true; setBusy(true); setError('');
    try {
      const result = await api(path, { method });
      setInvitation(method === 'DELETE' ? { token: null, requiresReissue: false } : { ...result, requiresReissue: false });
      onNotice(method === 'DELETE' ? '초대 링크를 비활성화했습니다.' : '새 초대 링크를 발급했습니다. 이전 링크는 사용할 수 없습니다.');
    } catch (failure) { setError(failure.message); }
    finally { operation.current = false; setBusy(false); }
  }

  async function copyInvitation() {
    try {
      await navigator.clipboard.writeText(`${location.origin}/#invite=${invitation.token}`);
      onCopied();
    } catch { onNotice('링크를 선택해 직접 복사해 주세요.'); }
  }

  return <section className="invite-card invite-card--compact" aria-label="모임 초대">
    <div className="invite-heading"><strong>모임 초대</strong><button type="button" className="text-button invite-toggle" aria-expanded={expanded} aria-controls={contentId} onClick={() => setExpanded(value => !value)}>{expanded ? '접기 ▴' : '펼치기 ▾'}</button></div>
    <div id={contentId} hidden={!expanded} aria-busy={loading || busy}>
      <p>링크를 전달하면 로그인한 사용자가 모임에 가입할 수 있습니다.</p>
      {loading && <p role="status">초대 링크를 불러오는 중입니다.</p>}
      <div className="button-row"><button type="button" className="secondary" disabled={loading || busy} onClick={() => setConfirmMethod('POST')}>새 링크 발급</button><button type="button" className="secondary" disabled={loading || busy} onClick={() => setConfirmMethod('DELETE')}>링크 비활성화</button></div>
      <small>새 링크를 발급하면 이전 링크는 사용할 수 없습니다.</small>
      {invitation?.requiresReissue && <p className="help">기존 초대 링크는 다시 표시할 수 없습니다. 한 번 새로 발급하면 이후에는 다른 기기에서도 확인할 수 있습니다.</p>}
      {!loading && invitation && !invitation.token && !invitation.requiresReissue && <p className="help">발급된 초대 링크가 없습니다.</p>}
      {invitation?.token && <><input readOnly aria-label="초대 링크" value={`${typeof location === 'undefined' ? '' : location.origin}/#invite=${invitation.token}`} onFocus={e => e.target.select()} /><button type="button" className="text-button invite-copy" onClick={copyInvitation}>링크 복사</button></>}
      {error && <p className="error" role="alert">{error}</p>}
    </div>
    {confirmMethod && <ConfirmInvitationDialog method={confirmMethod} onCancel={() => setConfirmMethod(null)} onConfirm={() => { const method = confirmMethod; setConfirmMethod(null); changeInvitation(method); }} />}
  </section>;
}
