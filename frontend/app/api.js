export async function api(path, options = {}) {
  const headers = { ...options.headers };
  if (options.body) headers['Content-Type'] = 'application/json';
  if (options.method && options.method !== 'GET') {
    const tokenResponse = await fetch('/api/auth/csrf', { credentials: 'same-origin', cache: 'no-store' });
    if (!tokenResponse.ok) throw new Error('로그인이 만료되었습니다. 다시 로그인해 주세요.');
    const csrf = await tokenResponse.json();
    headers[csrf.headerName] = csrf.token;
  }
  const response = await fetch(path, { ...options, headers, credentials: 'same-origin', cache: 'no-store' });
  if (!response.ok) {
    const messages = { 400: '입력값·참석자·팀 구성을 확인해 주세요.', 401: '다시 로그인해 주세요.', 403: '요청을 확인할 수 없습니다. 새로고침 후 다시 시도해 주세요.', 404: '대상을 찾을 수 없거나 접근 권한이 없습니다.', 409: '요청이 충돌했습니다. 목록을 새로 불러와 주세요.' };
    throw new Error(messages[response.status] || '서버 요청에 실패했습니다. 잠시 후 다시 시도해 주세요.');
  }
  return response.status === 204 ? null : response.json();
}

export const sports = { BASKETBALL: '농구', FUTSAL: '풋살', FOOTBALL: '축구', VOLLEYBALL: '배구', BASEBALL: '야구', LEAGUE_OF_LEGENDS: '리그 오브 레전드', OTHER: '기타' };
export const sportCode = label => Object.keys(sports).find(code => sports[code] === label);
export const teamPlayers = result => result.teams.map(team => team.members.map(member => ({ ...member, id: member.memberId })));
export const matchView = result => ({ id: result.id, date: result.playedAt, sport: sports[result.sportType], teams: teamPlayers(result) });
