'use client';

import { useEffect, useId, useRef, useState } from 'react';

export default function AlertModal({ message, onDismiss, autoDismissMs = 1000 }) {
  const dialog = useRef(null);
  const titleId = useId();
  const messageId = useId();
  const [closing, setClosing] = useState(false);

  useEffect(() => {
    const element = dialog.current;
    element.showModal();
    return () => element.close();
  }, []);

  useEffect(() => {
    const timer = setTimeout(() => setClosing(true), autoDismissMs);
    return () => clearTimeout(timer);
  }, [autoDismissMs]);

  useEffect(() => {
    if (!closing) return;
    const timer = setTimeout(() => onDismiss(''), 180);
    return () => clearTimeout(timer);
  }, [closing, onDismiss]);

  return <dialog ref={dialog} className={`modal alert-dialog ${closing ? 'alert-dialog--closing' : ''}`} role="alertdialog" aria-labelledby={titleId} aria-describedby={messageId} onCancel={event => { event.preventDefault(); setClosing(true); }}>
    <h2 id={titleId}>안내</h2>
    <p id={messageId}>{message}</p>
    <button type="button" className="primary" onClick={() => setClosing(true)}>확인</button>
  </dialog>;
}
