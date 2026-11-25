export function pendingAttendanceVotes(votes, now) {
  return sortAttendanceVotes(votes.filter(vote => vote.matchId == null), now);
}

export function sortAttendanceVotes(votes, now) {
  return [...votes].sort((a, b) => {
    const aTime = Date.parse(a.closesAt);
    const bTime = Date.parse(b.closesAt);
    const aClosed = a.closed || aTime <= now;
    const bClosed = b.closed || bTime <= now;
    if (aClosed !== bClosed) return aClosed ? 1 : -1;
    const difference = aClosed ? bTime - aTime : aTime - bTime;
    return difference || a.id - b.id;
  });
}
