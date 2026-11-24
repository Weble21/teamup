'use client';

import { useEffect, useId, useRef, useState } from 'react';
import { api } from './api';

export default function ClubInvitationPanel({ clubId, onNotice, onCopied }) {
  const [invitation, setInvitation] = useState(null);
  const [expanded, setExpanded] = useState(true);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
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
      <div className="button-row"><button type="button" className="secondary" disabled={loading || busy} onClick={() => changeInvitation('POST')}>새 링크 발급</button><button type="button" className="secondary" disabled={loading || busy} onClick={() => changeInvitation('DELETE')}>링크 비활성화</button></div>
      <small>새 링크를 발급하면 이전 링크는 사용할 수 없습니다.</small>
      {invitation?.requiresReissue && <p className="help">기존 초대 링크는 다시 표시할 수 없습니다. 한 번 새로 발급하면 이후에는 다른 기기에서도 확인할 수 있습니다.</p>}
      {!loading && invitation && !invitation.token && !invitation.requiresReissue && <p className="help">발급된 초대 링크가 없습니다.</p>}
      {invitation?.token && <><input readOnly aria-label="초대 링크" value={`${typeof location === 'undefined' ? '' : location.origin}/#invite=${invitation.token}`} onFocus={e => e.target.select()} /><button type="button" className="text-button invite-copy" onClick={copyInvitation}>링크 복사</button></>}
      {error && <p className="error" role="alert">{error}</p>}
    </div>
  </section>;
}
