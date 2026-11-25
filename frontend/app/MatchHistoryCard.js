import ExerciseScheduleCard from './ExerciseScheduleCard';

export default function MatchHistoryCard({ match, onOpenMatch, onOpenSchedule }) {
  if (match.attendanceVote) return <ExerciseScheduleCard vote={match.attendanceVote}
    onOpen={() => onOpenSchedule(match.attendanceVote.id)} actionLabel="경기 기록 보기" onAction={() => onOpenMatch(match)} />;
  return <button className="history-card" onClick={() => onOpenMatch(match)}>
    <small>{new Date(match.date).toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' })} · {match.sport}</small>
    <strong>팀 편성 기록 <span>→</span></strong>
    <small>{match.teams ? `참석 ${match.teams.flat().length}명 · ${match.teams.length}개 팀` : '서버 경기 기록'}</small>
  </button>;
}
