import { useEffect, useState } from 'react';

// Keep this in sync with the toast exit animation in globals.css.
const exitDurationMs = 180;

export default function Toast({ message, autoDismissMs = 1000, onDismiss }) {
  const [closing, setClosing] = useState(false);

  useEffect(() => {
    if (!autoDismissMs) return;
    const timer = setTimeout(() => setClosing(true), autoDismissMs);
    return () => clearTimeout(timer);
  }, [autoDismissMs]);

  useEffect(() => {
    if (!closing) return;
    const timer = setTimeout(() => onDismiss(''), exitDurationMs);
    return () => clearTimeout(timer);
  }, [closing, onDismiss]);

  return <div className={`toast ${closing ? 'toast--closing' : 'toast--opening'}`} role="status">
    {message}
    <button type="button" className="toast-close" aria-label="알림 닫기" onClick={() => setClosing(true)}>×</button>
  </div>;
}
