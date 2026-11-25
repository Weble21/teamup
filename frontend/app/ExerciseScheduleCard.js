export default function ExerciseScheduleCard({ vote, onOpen, actionLabel, onAction, actionDisabled = false }) {
  return <article className="attendance-list-item">
    <button type="button" className="attendance-list-main" onClick={onOpen}>
      <strong>{vote.title}</strong>
      <span>{vote.exerciseDate} · {vote.location}</span>
      <span>마감 {new Date(vote.closesAt).toLocaleString('ko-KR')}</span>
    </button>
    {onAction && <button type="button" className="vote-team-button" disabled={actionDisabled} onClick={onAction}>{actionLabel}</button>}
  </article>;
}
