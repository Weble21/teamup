'use client';

import { useEffect, useRef, useState } from 'react';
import Image from 'next/image';
import { api, sports, sportCode, teamPlayers, matchView } from './api';
import MemberImport from './MemberImport';
import UserGuide from './UserGuide';

const guestStorageKey = 'teamup:guest:v1';
const invitationStorageKey = 'teamup:invite:v1';
const uid = () => globalThis.crypto.randomUUID();
const average = (team) => team.length ? team.reduce((sum, p) => sum + p.rating, 0) / team.length : 0;
const displayRating = (rating) => rating / 10;
const blankMember = { name: '', position: '', rating: 50, age: '', notes: '' };

function SocialIcon({ provider }) {
  if (provider === 'google') return <svg viewBox="0 0 48 48" aria-hidden="true"><path fill="#4285F4" d="M43.6 24.5c0-1.4-.1-2.8-.4-4.1H24v7.8h11c-.5 2.5-1.9 4.6-4 6v5.1h6.4c3.8-3.5 6.2-8.6 6.2-14.8z"/><path fill="#34A853" d="M24 44c5.4 0 9.9-1.8 13.2-4.8l-6.4-5.1c-1.8 1.2-4 1.9-6.8 1.9-5.2 0-9.6-3.5-11.2-8.2H6.2v5.3A20 20 0 0 0 24 44z"/><path fill="#FBBC05" d="M12.8 27.8a12 12 0 0 1 0-7.6v-5.3H6.2a20 20 0 0 0 0 18.2l6.6-5.3z"/><path fill="#EA4335" d="M24 11.9c2.9 0 5.5 1 7.5 2.9l5.7-5.7A19.1 19.1 0 0 0 24 4 20 20 0 0 0 6.2 14.9l6.6 5.3c1.6-4.7 6-8.3 11.2-8.3z"/></svg>;
  if (provider === 'kakao') return <svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M12 3C6.5 3 2 6.5 2 10.8c0 2.8 1.9 5.3 4.8 6.7l-1.1 4c-.1.4.3.7.6.5l4.6-3.1c.4 0 .7.1 1.1.1 5.5 0 10-3.6 10-8.2C22 6.5 17.5 3 12 3z"/></svg>;
  return <span className="social-naver-icon" aria-hidden="true">N</span>;
}

function TeamInsight({ team, insight, status }) {
  if (status === 'guest') return <div className="team-insight"><small>AI 팀 분석은 로그인 모드에서 제공됩니다.</small></div>;
  if (status === 'loading') return <div className="team-insight"><small>AI가 팀 전력을 분석하고 있습니다…</small></div>;
  if (status === 'unavailable') return <div className="team-insight"><small>AI 분석을 불러오지 못했습니다. 팀 편성은 그대로 사용할 수 있습니다.</small></div>;
  if (!insight) return null;
  const player = team.find(member => member.id === insight.keyPlayerId);
  return <div className="team-insight"><strong>AI 팀 분석</strong><p><b>핵심 선수 · {player?.name}</b> — {insight.keyPlayerReason}</p><p><b>전력</b> — {insight.strength}</p><small>입력한 능력치와 포지션을 바탕으로 한 참고용 분석입니다.</small></div>;
}

async function loadServerClubs() {
  const rows = await api('/api/clubs');
  return Promise.all(rows.map(async row => ({ ...row, sport: sports[row.sportType],
    members: (await api(`/api/clubs/${row.id}/members`)).map(member => ({ ...member, age: member.age ?? '', notes: member.notes ?? '' })), matches: [],
  })));
}

function assign(players, count) {
  const teams = Array.from({ length: count }, () => []);
  [...players].sort(() => Math.random() - .5).sort((a, b) => b.rating - a.rating).forEach((p) => {
    const minSize = Math.min(...teams.map(t => t.length));
    const candidates = teams.filter(t => t.length === minSize).sort((a, b) => a.reduce((n, v) => n + v.rating, 0) - b.reduce((n, v) => n + v.rating, 0));
    candidates[0].push(p);
  });
  return teams;
}

export default function Home() {
  const [user, setUser] = useState(null);
  const [sportProfiles, setSportProfiles] = useState({});
  const [busy, setBusy] = useState(false);
  const [historyNext, setHistoryNext] = useState(false);
  const [historyPage, setHistoryPage] = useState(0);
  const operation = useRef(false);
  const confirmationId = useRef(null);
  const analysisRequestId = useRef(0);
  const choseGuest = useRef(false);
  const [clubs, setClubs] = useState([]);
  const [storageError, setStorageError] = useState('');
  const [screen, setScreen] = useState('welcome');
  const [clubId, setClubId] = useState(null);
  const [query, setQuery] = useState('');
  const [dialog, setDialog] = useState(null);
  const [importOpen, setImportOpen] = useState(false);
  const [pendingInvite, setPendingInvite] = useState('');
  const [inviteLink, setInviteLink] = useState('');
  const [form, setForm] = useState({});
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [teamCount, setTeamCount] = useState(2);
  const [conditionText, setConditionText] = useState('');
  const [interpretedConditions, setInterpretedConditions] = useState([]);
  const [attendees, setAttendees] = useState([]);
  const [teams, setTeams] = useState([]);
  const [analysis, setAnalysis] = useState(null);
  const [analysisStatus, setAnalysisStatus] = useState('idle');
  const [swap, setSwap] = useState(null);
  const [historyItem, setHistoryItem] = useState(null);
  const club = clubs.find(c => c.id === clubId);
  const isManager = !user || club?.ownerId === user.id;
  const myProfile = club?.members.find(member => member.mine);
  const activeMembers = club?.members.filter(m => m.active) ?? [];
  const positionExample = club?.sportType === 'BASKETBALL'
    ? '가드, 센터, 포워드를 골고루 나눠줘'
    : ['FOOTBALL', 'FUTSAL'].includes(club?.sportType)
      ? 'LWB, MF, LW, ST를 골고루 나눠줘'
      : '포지션을 골고루 나눠줘';
  const conditionExample = `예: 민수와 철수는 다른 팀\n예: ${positionExample}`;

  useEffect(() => {
    let cancelled = false;
    const token = new URLSearchParams(location.hash.slice(1)).get('invite');
    if (token && /^[A-Za-z0-9_-]{43}$/.test(token)) {
      sessionStorage.setItem(invitationStorageKey, token);
      history.replaceState(null, '', location.pathname + location.search);
    }
    const storedInvite = sessionStorage.getItem(invitationStorageKey);
    async function restore() {
      if (new URLSearchParams(location.search).has('loginError')) setNotice('소셜 로그인에 실패했습니다. 설정과 동의를 확인해 주세요.');
      try {
        const response = await fetch('/api/users/me', { cache: 'no-store' });
        if (!cancelled && storedInvite) setPendingInvite(storedInvite);
        if (!response.ok) {
          if (new URLSearchParams(location.search).has('signedIn')) setNotice('로그인 세션을 확인할 수 없습니다. 다시 로그인해 주세요.');
          return;
        }
        const current = await response.json();
        const [rows, profiles] = await Promise.all([loadServerClubs(), api('/api/users/me/sport-profiles')]);
        if (!cancelled && !choseGuest.current) { setUser(current); setClubs(rows); setSportProfiles(Object.fromEntries(profiles.map(profile => [profile.sportType, profile]))); setScreen('clubs'); }
      } catch { if (!cancelled && storedInvite) setPendingInvite(storedInvite); if (!cancelled && new URLSearchParams(location.search).has('signedIn')) setNotice('서버 연결을 확인해 주세요.'); }
      if (!cancelled && new URLSearchParams(location.search).has('loginError')) setNotice('소셜 로그인에 실패했습니다. 설정과 동의를 확인해 주세요.');
    }
    restore();
    return () => { cancelled = true; };
  }, []);

  async function serverOperation(action) {
    if (operation.current) return;
    operation.current = true; setBusy(true); setError('');
    try { await action(); } catch (failure) { setError(failure.message); setNotice(failure.message); }
    finally { operation.current = false; setBusy(false); }
  }

  function roster() { return { memberIds: attendees, teams: teams.map(team => team.map(member => member.id)) }; }

  function joinInvitation() {
    return serverOperation(async () => {
      const joined = await api('/api/invitations/join', { method: 'POST', body: JSON.stringify({ token: pendingInvite }) });
      sessionStorage.removeItem(invitationStorageKey); setPendingInvite('');
      setClubs(await loadServerClubs()); setClubId(joined.clubId); setScreen('detail'); setNotice('모임에 가입했습니다. 내 정보를 등록해 주세요.');
    });
  }
  function issueInvitation() {
    return serverOperation(async () => {
      const result = await api(`/api/clubs/${clubId}/invitation`, { method: 'POST' });
      setInviteLink(`${location.origin}/#invite=${result.token}`);
      setNotice('새 초대 링크를 발급했습니다. 이전 링크는 사용할 수 없습니다.');
    });
  }
  function revokeInvitation() {
    return serverOperation(async () => {
      await api(`/api/clubs/${clubId}/invitation`, { method: 'DELETE' });
      setInviteLink(''); setNotice('초대 링크를 비활성화했습니다.');
    });
  }
  async function copyInvitation() {
    try {
      if (!navigator.clipboard) throw new Error('Clipboard unavailable');
      await navigator.clipboard.writeText(inviteLink);
      setNotice('초대 링크를 복사했습니다.');
    } catch { setNotice('링크를 선택해 직접 복사해 주세요.'); }
  }

  function openHistory() {
    if (!user) { setScreen('history'); return; }
    return serverOperation(async () => {
      const result = await api(`/api/clubs/${clubId}/matches`);
      setClubs(old => old.map(c => c.id === clubId ? { ...c, matches: result.matches.map(m => ({ ...m, date: m.playedAt, sport: sports[m.sportType] })) } : c));
      setHistoryPage(0); setHistoryNext(result.hasNext); setScreen('history');
    });
  }

  function moreHistory() {
    return serverOperation(async () => {
      const result = await api(`/api/clubs/${clubId}/matches?page=${historyPage + 1}`);
      setClubs(old => old.map(c => c.id === clubId ? { ...c, matches: [...c.matches, ...result.matches.map(m => ({ ...m, date: m.playedAt, sport: sports[m.sportType] }))] } : c));
      setHistoryPage(result.page); setHistoryNext(result.hasNext);
    });
  }

  function openMatch(match) {
    if (!user) { setHistoryItem(match); setScreen('match'); return; }
    return serverOperation(async () => { setHistoryItem(matchView(await api(`/api/clubs/${clubId}/matches/${match.id}`))); setScreen('match'); });
  }

  function logout() {
    return serverOperation(async () => {
      await api('/api/auth/logout', { method: 'POST' });
      setUser(null); setClubs([]); setSportProfiles({}); setClubId(null); setScreen('welcome'); setNotice('로그아웃했습니다.');
    });
  }

  function enterGuest() {
    choseGuest.current = true;
    setUser(null);
    try {
      const raw = localStorage.getItem(guestStorageKey);
      const saved = raw === null ? [] : JSON.parse(raw);
      if (!Array.isArray(saved) || !saved.every(c => c && typeof c.id === 'string' && typeof c.name === 'string' && typeof c.sport === 'string' && Array.isArray(c.members) && Array.isArray(c.matches))) throw new Error('Invalid guest data');
      setClubs(saved); setStorageError(''); setScreen('clubs');
    } catch {
      setStorageError('게스트 데이터를 읽을 수 없습니다. 브라우저 저장 공간을 확인해 주세요. 기존 데이터는 유지됩니다.');
    }
  }
  function persistClubs(next) {
    try {
      localStorage.setItem(guestStorageKey, JSON.stringify(next));
      setClubs(next); setStorageError(''); return true;
    } catch {
      setStorageError('저장에 실패했습니다. 브라우저 저장 공간과 설정을 확인한 뒤 다시 시도해 주세요.'); return false;
    }
  }
  function updateClub(fn) { return persistClubs(clubs.map(c => c.id === clubId ? fn(c) : c)); }
  function openDialog(type, value = {}) { setForm(value); setError(''); setDialog(type); }
  function openMySportProfile(sportType) {
    openDialog('sportProfile', { ...blankMember, name: user.name, ...sportProfiles[sportType], sportType });
  }
  function openNewMember() {
    const sportType = sportCode(club.sport);
    const profile = user && sportProfiles[sportType];
    const hasOwnMember = club.members.some(member => member.mine);
    openDialog('member', profile && !hasOwnMember
      ? { ...blankMember, ...profile, age: profile.age ?? '', notes: profile.notes ?? '', fromMyProfile: true }
      : { ...blankMember });
  }
  function closeDialog() { setDialog(null); setError(''); }
  function saveSportProfile(e) {
    e.preventDefault();
    const rating = Number(form.rating), age = form.age === '' || form.age == null ? null : Number(form.age);
    if (!form.name?.trim() || !form.position?.trim()) return setError('이름과 포지션을 입력해 주세요.');
    if (!Number.isInteger(rating) || rating < 1 || rating > 100 || (age !== null && (!Number.isInteger(age) || age < 0 || age > 130))) return setError('능력치와 나이를 확인해 주세요.');
    return serverOperation(async () => {
      const saved = await api(`/api/users/me/sport-profiles/${form.sportType}`, { method: 'PUT', body: JSON.stringify({ name: form.name.trim(), position: form.position.trim(), rating, age, notes: form.notes?.trim() || null }) });
      setSportProfiles(old => ({ ...old, [saved.sportType]: saved })); closeDialog(); setNotice(`${sports[saved.sportType]} 내 정보를 저장했습니다.`);
    });
  }
  function saveClub(e) {
    e.preventDefault();
    if (!form.name?.trim() || !form.sport) return setError('모임 이름과 종목을 입력해 주세요.');
    if (user) return serverOperation(async () => {
      await api(form.id ? `/api/clubs/${form.id}` : '/api/clubs', { method: form.id ? 'PUT' : 'POST', body: JSON.stringify({ name: form.name.trim(), sportType: sportCode(form.sport) }) });
      setClubs(await loadServerClubs()); closeDialog();
    });
    if (persistClubs([...clubs, { id: uid(), name: form.name.trim(), sport: form.sport, members: [], matches: [] }])) closeDialog();
  }
  function saveMember(e) {
    e.preventDefault();
    const rating = Number(form.rating), age = form.age === '' || form.age == null ? null : Number(form.age);
    if (!form.name?.trim() || !form.position?.trim()) return setError('이름과 포지션을 입력해 주세요.');
    if (!Number.isInteger(rating) || rating < 1 || rating > 100) return setError('능력치를 확인해 주세요.');
    if (age !== null && (!Number.isInteger(age) || age < 0 || age > 130)) return setError('나이를 확인해 주세요.');
    if (user) return serverOperation(async () => {
      const payload = { name: form.name.trim(), position: form.position.trim(), rating, age, notes: form.notes?.trim() || null };
      const path = `/api/clubs/${clubId}/members`;
      if (!isManager || form.fromMyProfile) {
        await api(`${path}/me`, { method: 'PUT', body: JSON.stringify(payload) });
      } else if (form.id) {
        await api(`${path}/${form.id}`, { method: 'PUT', body: JSON.stringify(payload) });
        if (!form.active) await api(`${path}/${form.id}/deactivate`, { method: 'PATCH' });
      } else await api(path, { method: 'POST', body: JSON.stringify(payload) });
      setClubs(await loadServerClubs()); closeDialog();
    });
    const member = { ...form, id: form.id ?? uid(), name: form.name.trim(), position: form.position.trim(), rating, age: age ?? '', notes: form.notes?.trim() ?? '', active: form.active ?? true };
    if (updateClub(c => ({ ...c, members: form.id ? c.members.map(m => m.id === form.id ? member : m) : [...c.members, member] }))) closeDialog();
  }
  function saveImportedMembers(members) {
    if (user) return serverOperation(async () => {
      await api(`/api/clubs/${clubId}/members/bulk`, { method: 'POST', body: JSON.stringify({ members }) });
      setImportOpen(false);
      try { setClubs(await loadServerClubs()); setNotice(`${members.length}명을 등록했습니다.`); }
      catch { setNotice('회원은 등록되었지만 목록을 불러오지 못했습니다. 새로고침해 주세요.'); }
    });
    const newMembers = members.map(member => ({ ...member, id: uid(), age: '', notes: '', active: true }));
    if (updateClub(c => ({ ...c, members: [...c.members, ...newMembers] }))) {
      setImportOpen(false); setNotice(`${members.length}명을 등록했습니다.`);
    }
  }
  function setUnknownRating() {
    const rated = activeMembers.filter(m => m.id !== form.id && Number.isInteger(m.rating));
    if (!rated.length) return setError('기준이 되는 능력치가 없습니다. 직접 입력해 주세요.');
    const level = Math.max(1, Math.min(10, Math.round((rated.reduce((n, m) => n + m.rating, 0) / rated.length - 5) / 10)));
    setForm(f => ({ ...f, rating: level * 10 })); setError('');
  }
  function startFormation() { analysisRequestId.current++; setAnalysis(null); setAnalysisStatus('idle'); setConditionText(''); setInterpretedConditions([]); setAttendees(activeMembers.map(m => m.id)); setTeamCount(2); setScreen('formation'); setNotice(''); }
  async function analyzeAssignment(result, memberIds) {
    const requestId = ++analysisRequestId.current;
    setAnalysis(null); setAnalysisStatus('loading');
    try {
      const response = await api(`/api/clubs/${clubId}/team-assignments/analyze`, { method: 'POST', body: JSON.stringify({ memberIds, teams: result.teams.map(team => team.members.map(member => member.memberId)) }) });
      if (analysisRequestId.current === requestId) { setAnalysis(response.teams); setAnalysisStatus('ready'); }
    } catch {
      if (analysisRequestId.current === requestId) setAnalysisStatus('unavailable');
    }
  }
  function generate() {
    const players = activeMembers.filter(p => attendees.includes(p.id));
    if (players.length < teamCount) return setNotice('각 팀에 한 명 이상 배정할 수 있도록 참석자를 선택해 주세요.');
    if (user) return serverOperation(async () => {
      const result = await api(`/api/clubs/${clubId}/team-assignments`, { method: 'POST', body: JSON.stringify({ memberIds: attendees, teamCount, conditionText: conditionText.trim() || null }) });
      setTeams(teamPlayers(result)); setInterpretedConditions(result.interpretedConditions ?? []); confirmationId.current = uid(); setSwap(null); setNotice(''); setScreen('result');
      analyzeAssignment(result, attendees);
    });
    analysisRequestId.current++; setAnalysis(null); setAnalysisStatus('guest');
    setTeams(assign(players, teamCount)); setInterpretedConditions([]); setSwap(null); setNotice(''); setScreen('result');
  }
  function swapMember(teamIndex, memberIndex) {
    if (!swap) { setSwap({ teamIndex, memberIndex }); return; }
    if (swap.teamIndex === teamIndex) { setSwap({ teamIndex, memberIndex }); return; }
    if (user) return serverOperation(async () => {
      const result = await api(`/api/clubs/${clubId}/team-assignments/swap`, { method: 'POST', body: JSON.stringify({
        ...roster(), firstMemberId: teams[swap.teamIndex][swap.memberIndex].id, secondMemberId: teams[teamIndex][memberIndex].id, conditionText: conditionText.trim() || null,
      }) });
      setTeams(teamPlayers(result)); setInterpretedConditions(result.interpretedConditions ?? []); confirmationId.current = uid(); setSwap(null);
      analyzeAssignment(result, attendees);
    });
    setTeams(old => { const next = old.map(t => [...t]); [next[swap.teamIndex][swap.memberIndex], next[teamIndex][memberIndex]] = [next[teamIndex][memberIndex], next[swap.teamIndex][swap.memberIndex]]; return next; }); setSwap(null);
  }
  function saveMatch() {
    if (user) return serverOperation(async () => {
      confirmationId.current ??= uid();
      const saved = await api(`/api/clubs/${clubId}/matches`, { method: 'POST', body: JSON.stringify({ ...roster(), requestId: confirmationId.current }) });
      setHistoryItem(matchView(saved)); setScreen('match'); setNotice('경기 기록을 서버에 저장했습니다.');
    });
    const snapshot = teams.map(t => t.map(({ id, name, position, rating }) => ({ id, name, position, rating })));
    const match = { id: uid(), date: new Date().toISOString(), sport: club.sport, teams: snapshot };
    if (updateClub(c => ({ ...c, matches: [match, ...c.matches] }))) { setScreen('history'); setNotice('경기 기록을 이 브라우저에 저장했습니다.'); }
  }
  const back = () => { if (user && screen === 'match') return openHistory(); setNotice(''); setScreen(({ clubs: 'welcome', detail: 'clubs', members: 'detail', formation: 'detail', result: 'formation', history: 'detail', match: 'history', guide: 'clubs', my: 'clubs' })[screen] ?? 'clubs'); };
  const title = ({ detail: club?.name, members: '회원 관리', formation: '팀 나누기', result: '팀 편성 결과', history: '경기 기록', match: '경기 상세', guide: '설명서', my: '내 계정' })[screen];
  return <div className="shell">
    <aside className="desktop-brand"><div className="brand">TEAM <span>/</span> UP</div><div className="side-line"/><p>함께 뛰는 시간,<br/><strong>더 좋은 팀을 만듭니다.</strong></p><small>모임부터 경기 기록까지<br/>당신의 다음 경기를 준비하세요.</small><div className="side-footer">PLAY TOGETHER · PLAY BETTER</div></aside>
    <div className="app" aria-busy={busy} style={busy ? { pointerEvents: 'none', opacity: .7 } : undefined}>
      {screen !== 'welcome' && <header className="header">{screen === 'clubs' ? <div className="brand">TEAM <span>/</span> UP</div> : <><button className="back" onClick={back} aria-label="뒤로 가기">‹</button><h1>{title}</h1></>}{user ? <button className="preview-chip" onClick={logout} aria-label="로그아웃"><span className="preview-name">{user.name}</span><span>· 로그아웃</span></button> : <span className="preview-chip">게스트</span>}</header>}
      <main className={screen === 'welcome' ? 'welcome' : 'content'}>
        {storageError && !dialog && <p className="error" role="alert">{storageError}</p>}
        {pendingInvite && ['welcome','clubs'].includes(screen) && <div className="invite-card"><strong>모임 초대</strong><p>{user ? '초대를 수락하고 모임에 가입하세요.' : '초대된 모임에 가입하려면 먼저 로그인하세요.'}</p>{user && <button className="primary" onClick={joinInvitation}>초대 수락</button>}<button className="text-button" onClick={() => { sessionStorage.removeItem(invitationStorageKey); setPendingInvite(''); }}>초대 닫기</button></div>}
        {screen === 'welcome' && <><section className="hero"><div className="red-line"/><div className="brand light">TEAM <span>/</span> UP</div><div className="hero-copy"><div className="eyebrow">MAKE YOUR GAME BETTER</div><h1>좋은 경기는<br/>균형 잡힌 팀에서<span>.</span></h1><p>모이고, 나누고, 함께 즐겨요.</p></div><Image className="logo" src="/teamup-logo.png" alt="TEAM UP 엠블럼" width={100} height={100} priority /></section><section className="intro"><div className="eyebrow blue">READY TO PLAY?</div><h2>우리의 다음 경기를 시작해요</h2><p>모임과 선수 명단을 한 곳에서 관리하세요.</p><div className="social-logins">{['kakao','naver','google'].map(provider => <a className={'social-login social-login--' + provider} key={provider} href={'/oauth2/authorization/' + provider}><span className="social-icon"><SocialIcon provider={provider} /></span><span>{({google:'Google',kakao:'카카오',naver:'네이버'})[provider]}로 계속하기</span></a>)}</div><button className="primary" onClick={enterGuest}>게스트로 시작하기 <span>→</span></button><div className="preview-note">데이터는 이 브라우저에만 저장됩니다. 브라우저 데이터를 삭제하면 함께 사라집니다.</div></section></>}
        {screen === 'guide' && <UserGuide />}
        {screen === 'clubs' && <><div className="heading"><div className="eyebrow blue">YOUR COMMUNITY</div><h2>내 모임</h2><p>함께 뛰는 모임을 선택해 주세요</p></div><div className="stack">{clubs.map((c, i) => <button key={c.id} className={'club-card ' + (i === 0 ? 'featured' : '')} onClick={() => {setInviteLink(''); setClubId(c.id); setScreen('detail');}}><div className="club-top"><span className="club-icon">{c.sport[0]}</span><span className="eyebrow">{c.sport.toUpperCase()} CLUB</span><span className="arrow">↗</span></div><strong>{c.name}</strong><small>{c.sport} <i>·</i> 등록 회원 {user ? c.activeMemberCount : c.members.filter(m => m.active).length}명</small></button>)}<button className="outline-action" onClick={() => openDialog('club', { name: '', sport: '농구' })}>＋ &nbsp;새 모임 만들기</button></div></>}
        {screen === 'my' && <><div className="heading"><div className="eyebrow blue">MY ACCOUNT</div><h2>{user ? user.name : '게스트'}</h2><p>{user ? '가입한 모임과 내 정보를 확인하세요.' : '이 브라우저에서 게스트로 사용 중입니다.'}</p></div>{user ? <><div className="section-heading"><div><div className="eyebrow blue">MY SPORTS</div><h2>종목별 내 정보</h2></div></div><p className="help">저장한 정보는 같은 종목의 모임에서 내 회원 정보를 추가할 때 입력됩니다.</p><div className="stack compact">{Object.entries(sports).map(([code, label]) => <button key={code} className="member-card" onClick={() => openMySportProfile(code)}><span className="avatar">{label[0]}</span><span className="member-name"><strong>{label}</strong><small>{sportProfiles[code] ? `${sportProfiles[code].position} · ${displayRating(sportProfiles[code].rating)} / 10` : "정보 등록"}</small></span><span className="chevron">›</span></button>)}</div><div className="section-heading"><div><div className="eyebrow blue">MY CLUBS</div><h2>가입한 모임</h2></div></div><div className="stack compact">{clubs.map(c => <button key={c.id} className="member-card" onClick={() => { setInviteLink(''); setClubId(c.id); setScreen('detail'); }}><span className="avatar">{c.name[0]}</span><span className="member-name"><strong>{c.name}</strong><small>{c.ownerId === user.id ? '관리자 · 모임 관리' : c.members.some(m => m.mine) ? '멤버 · 내 정보 보기' : '멤버 · 내 정보 등록'}</small></span><span className="chevron">›</span></button>)}{clubs.length === 0 && <div className="empty">가입한 모임이 없습니다.</div>}</div><button className="secondary my-logout" onClick={logout}>로그아웃</button></> : <><p className="help">게스트 모임은 이 브라우저에만 저장됩니다.</p><button className="secondary my-logout" onClick={() => setScreen('welcome')}>로그인 화면으로 이동</button></>}</>}
        {screen === 'detail' && !isManager && <><div className="meta">{club.sport} CLUB · 참여 멤버</div><div className="section-heading"><div><div className="eyebrow blue">MY PROFILE</div><h2>내 정보</h2></div></div>{myProfile ? <button className="member-card" disabled={!myProfile.active} onClick={() => openDialog('member', {...myProfile})}><span className="avatar">{myProfile.name[0]}</span><span className="member-name"><strong>{myProfile.name}</strong><small>{myProfile.position} · {displayRating(myProfile.rating)}점{!myProfile.active && ' · 비활성'}</small></span><span className="chevron">›</span></button> : <button className="primary" onClick={openNewMember}>내 정보 등록</button>}<p className="help">이 모임에서는 자신의 정보만 등록하거나 수정할 수 있습니다.</p><div className="section-heading"><div><div className="eyebrow blue">THE ROSTER</div><h2>멤버 목록</h2></div></div><div className="stack compact">{activeMembers.map(member => <div className="member-card" key={member.id}><span className="avatar">{member.name[0]}</span><span className="member-name"><strong>{member.name}{member.mine && ' (나)'}</strong><small>{member.position}</small></span><span className="rating">{displayRating(member.rating)}<small> / 10</small></span></div>)}{!activeMembers.length && <div className="empty">등록된 멤버가 없습니다.</div>}</div><button className="secondary import-button" onClick={openHistory}>경기 기록 보기</button></>}
        {screen === 'detail' && isManager && <><div className="meta">{club.sport} CLUB <span>/</span> 등록 회원 {activeMembers.length}명</div>{user && <><button className="secondary" onClick={() => openDialog('club', {id: club.id, name: club.name, sport: club.sport})}>모임 정보 수정</button><div className="invite-card"><strong>모임 초대</strong><p>링크를 전달하면 로그인한 사용자가 모임에 가입할 수 있습니다.</p><div className="button-row"><button className="secondary" onClick={issueInvitation}>새 링크 발급</button><button className="secondary" onClick={revokeInvitation}>링크 비활성화</button></div><small>새 링크를 발급하면 이전 링크는 사용할 수 없습니다.</small>{inviteLink && <><input readOnly aria-label="초대 링크" value={inviteLink} onFocus={e => e.target.select()} /><button className="text-button" onClick={copyInvitation}>링크 복사</button></>}</div></>}<div className="battle"><div className="eyebrow">READY FOR</div><h2>YOUR NEXT<br/>BATTLE<span>.</span></h2><p>오늘은 어떤 팀으로 뛸까요?</p><div className="battle-mark">✳</div></div><div className="section-heading"><div><div className="eyebrow blue">CLUB MENU</div><h2>무엇을 할까요?</h2></div><small>SELECT MENU</small></div><div className="stack">{[['01','팀 나누기','참석자를 선택하고 팀을 구성해요','formation'],['02','회원 관리','선수 정보와 능력치를 관리해요','members'],['03','이전 경기 기록','지난 팀 편성과 경기 결과를 확인해요','history']].map(([n,t,d,fn]) => <button className="menu-card" key={n} onClick={() => { if (fn === 'formation') startFormation(); else if (fn === 'history') openHistory(); else { setQuery(''); setScreen('members'); } }}><span className="number">{n}</span><span><strong>{t}</strong><small>{d}</small></span><span className="chevron">→</span></button>)}</div></>}
        {screen === 'members' && <><div className="meta">{club.name} <span>·</span> 활성 회원 {activeMembers.length}명</div><button className="secondary import-button" onClick={() => setImportOpen(true)}>사진·Excel로 회원 추가</button><input className="search" placeholder="⌕  이름으로 검색" value={query} onChange={e => setQuery(e.target.value)} aria-label="회원 이름 검색"/><div className="section-heading"><div><div className="eyebrow blue">THE ROSTER</div><h2>선수 명단</h2></div><button className="text-button" onClick={openNewMember}>＋ 회원 추가</button></div><div className="stack compact">{club.members.filter(m => m.name.includes(query)).map(m => <button key={m.id} className={'member-card ' + (!m.active ? 'inactive' : '')} onClick={() => openDialog('member', {...m})}><span className="avatar">{m.name[0]}</span><span className="member-name"><strong>{m.name} {!m.active && <em>비활성</em>}</strong><small>{m.position}</small></span><span className="rating">{displayRating(m.rating)}<small> / 10</small></span><span className="chevron">›</span></button>)}{!club.members.filter(m => m.name.includes(query)).length && <div className="empty">표시할 선수가 없습니다.</div>}</div></>}
        {screen === 'formation' && <><div className="meta">참석자와 편성 조건을 선택해 주세요</div><div className="section-heading"><div><div className="eyebrow blue">01 / TEAM SETUP</div><h2>팀 수</h2></div></div><div className="stepper"><button aria-label="팀 수 줄이기" disabled={teamCount <= 2} onClick={() => setTeamCount(n => n-1)}>−</button><strong>{teamCount} <small>팀</small></strong><button aria-label="팀 수 늘리기" disabled={teamCount >= 10} onClick={() => setTeamCount(n => n+1)}>＋</button></div><div className="section-heading"><div><div className="eyebrow blue">02 / SELECT PLAYERS</div><h2>참석자 선택</h2></div><span className="selected-count">{attendees.length}명 선택</span></div><div className="stack compact">{activeMembers.map(m => <label className="select-card" key={m.id}><input type="checkbox" checked={attendees.includes(m.id)} onChange={e => setAttendees(old => e.target.checked ? [...old,m.id] : old.filter(id => id !== m.id))}/><strong>{m.name}</strong><small>{m.position} · {displayRating(m.rating)}점</small></label>)}{!activeMembers.length && <div className="empty">회원 관리에서 선수를 먼저 추가해 주세요.</div>}</div><div className="section-heading"><div><div className="eyebrow blue">03 / CONDITIONS</div><h2>추가 조건 <small>선택</small></h2></div></div>{user ? <><textarea className="conditions-input" maxLength={500} value={conditionText} onChange={e => setConditionText(e.target.value)} placeholder={conditionExample} aria-label="팀 편성 조건" /><p className="help">한 줄에 하나씩 최대 5개. 같은 팀·다른 팀 조건에는 참석 선수의 이름 2명을 정확히 입력하세요.</p></> : <div className="disabled-box">자연어 조건은 로그인 모드에서 사용할 수 있습니다.</div>}{notice && <p className="error" role="alert">{notice}</p>}<button className="primary bottom-action" onClick={() => generate()}>팀 나누기 <span>→</span></button><p className="help">미리보기는 능력치 중심의 간단한 편성으로, 최적해를 보장하지 않습니다. 동일한 입력은 동일한 서버 결과를 반환합니다.</p></>}
        {screen === 'result' && <><div className="meta">다른 팀의 선수를 차례로 선택해 교환하세요</div><div className="balance"><span>평균 능력치 차이</span><strong>{displayRating(Math.max(...teams.map(average))-Math.min(...teams.map(average))).toFixed(1)} <small>POINTS</small></strong></div><div className="stack">{teams.map((team, i) => <section className={'team-card tone-'+(i%2)} key={i}><div className="team-header"><div><div className="eyebrow">LINEUP {String(i+1).padStart(2,'0')}</div><h2>TEAM {String.fromCharCode(65+i)}</h2></div><div className="team-score"><strong>{displayRating(average(team)).toFixed(1)}</strong><small>평균 · 총점 {displayRating(team.reduce((s,p)=>s+p.rating,0))}</small></div></div>{team.map((p,j) => <button className={'team-player '+(swap?.teamIndex===i && swap?.memberIndex===j?'chosen':'')} key={p.id} onClick={() => swapMember(i,j)}><strong>{p.name}</strong><span>{p.position}</span><b>{displayRating(p.rating)}점</b></button>)}<TeamInsight team={team} insight={analysis?.find(item => item.teamNumber === i + 1)} status={analysisStatus} /></section>)}</div>{interpretedConditions.length > 0 && <p className="help">적용한 조건: {interpretedConditions.join(' · ')}</p>}{swap && <p className="help">교환할 다른 팀의 선수를 선택해 주세요.</p>}<div className="button-row"><button className="secondary" onClick={() => generate()}>다시 나누기</button><button className="secondary" onClick={() => setSwap(null)}>선택 취소</button></div><button className="primary" onClick={() => saveMatch()}>결과 확정하기 <span>→</span></button><p className="help">{user ? '확정 시 서버에서 다시 검증하고 경기 기록으로 저장합니다.' : '확정 결과는 이 브라우저에만 저장됩니다.'}</p></>}
        {screen === 'history' && <><div className="meta">{club.name}의 지난 경기를 모았어요</div><div className="section-heading"><div><div className="eyebrow blue">MATCH ARCHIVE</div><h2>최근 경기</h2></div></div><div className="stack">{club.matches.length === 0 && <div className="empty">아직 저장된 경기 기록이 없습니다.<br/>첫 경기를 만들어 보세요.</div>}{club.matches.map(match => <button key={match.id} className="history-card" onClick={() => openMatch(match)}><small>{new Date(match.date).toLocaleDateString('ko-KR', {year:'numeric',month:'long',day:'numeric'})} · {match.sport}</small><strong>팀 편성 기록 <span>→</span></strong><small>{match.teams ? `참석 ${match.teams.flat().length}명 · ${match.teams.length}개 팀` : '서버 경기 기록'}</small></button>)}</div></>}
        {screen === 'match' && historyItem && <><div className="meta">{new Date(historyItem.date).toLocaleDateString('ko-KR')} · {historyItem.sport}</div><div className="section-heading"><div><div className="eyebrow blue">MATCH LINEUP</div><h2>당시 팀 구성</h2></div></div><div className="stack">{historyItem.teams.map((team,i) => <section className="team-card" key={i}><div className="team-header"><h2>TEAM {String.fromCharCode(65+i)}</h2><div className="team-score"><strong>{displayRating(average(team)).toFixed(1)}</strong><small>평균 · 총점 {displayRating(team.reduce((n,p)=>n+p.rating,0))}</small></div></div>{team.map(p => <div className="team-player" key={p.id}><strong>{p.name}</strong><span>{p.position}</span><b>{displayRating(p.rating)}점</b></div>)}</section>)}</div><p className="help">기록 당시의 회원 정보입니다.</p></>}
      </main>
      {screen !== 'welcome' && <nav className="bottom-nav" aria-label="하단 메뉴"><button className={['clubs','detail','members','formation','result','history','match'].includes(screen)?'active':''} onClick={() => setScreen('clubs')}><span>◉</span> 홈 화면</button><button className={screen === 'guide' ? 'active' : ''} onClick={() => setScreen('guide')}><span>▤</span> 설명서</button><button className={screen === 'my' ? 'active' : ''} onClick={() => setScreen('my')}><span>○</span> MY</button></nav>}
      {screen === 'history' && user && historyNext && <button className="secondary" onClick={() => moreHistory()}>기록 더 보기</button>}{notice && screen !== 'formation' && <div className="toast" role="status" onClick={() => setNotice('')}>{notice} <span>×</span></div>}
    </div>
    {importOpen && <MemberImport onClose={() => !busy && setImportOpen(false)} onSave={saveImportedMembers} busy={busy} saveError={error || storageError} />}
    {dialog && <div className="overlay" onClick={closeDialog}><section className="modal" role="dialog" aria-modal="true" aria-labelledby="dialog-title" onClick={e => e.stopPropagation()}><div className="modal-heading"><div><div className="eyebrow blue">{dialog === 'club' ? 'NEW CLUB' : dialog === 'sportProfile' ? 'MY SPORT PROFILE' : 'PLAYER PROFILE'}</div><h2 id="dialog-title">{dialog === 'club' ? '새 모임 만들기' : dialog === 'sportProfile' ? `${sports[form.sportType]} 내 정보` : form.id ? '회원 정보 수정' : '새 회원 추가'}</h2></div><button className="close" onClick={closeDialog} aria-label="닫기">×</button></div><form aria-busy={busy} style={busy ? { pointerEvents: 'none' } : undefined} onSubmit={dialog === 'club' ? saveClub : dialog === 'sportProfile' ? saveSportProfile : saveMember}>
      <label>이름 <b>*</b><input maxLength={40} value={form.name ?? ''} onChange={e => setForm({...form,name:e.target.value})} placeholder={dialog === 'club'?'예: 주말 농구 모임':'선수 이름'} /></label>
      {dialog === 'club' ? <label>운동 종목 <b>*</b><select value={form.sport ?? '농구'} onChange={e => setForm({...form,sport:e.target.value})}>{['농구','풋살','축구','배구','야구','리그 오브 레전드','기타'].map(s=><option key={s}>{s}</option>)}</select></label> : <><label>포지션 <b>*</b><input maxLength={30} value={form.position ?? ''} onChange={e => setForm({...form,position:e.target.value})} placeholder="예: 가드" /></label><label className="rating-field">능력치 <b>*</b><span className="rating-value">{displayRating(form.rating)} <small>/ 10</small></span><input type="range" min="1" max="10" step="1" value={Math.max(1, Math.min(10, Math.round(form.rating / 10)))} onChange={e => setForm({...form,rating:Number(e.target.value) * 10})} aria-valuetext={displayRating(form.rating) + '점'} /><span className="rating-scale"><span>1</span><span>10</span></span></label>{form.rating % 10 !== 0 && <p className="help">기존 능력치가 표시됩니다. 바를 조절하면 1~10의 정수로 변경됩니다.</p>}<label>나이 <small>선택</small><input type="number" min="0" max="130" value={form.age ?? ''} onChange={e => setForm({...form,age:e.target.value})} placeholder="입력 안 함" /></label>{dialog !== 'sportProfile' && <button type="button" className="text-button estimate" onClick={setUnknownRating}>실력을 잘 모르겠어요 · 모임 평균 기준 적용</button>}{dialog !== 'sportProfile' && <p className="help">활성 회원 평균에서 0.5점을 뺀 뒤 1~10 사이 정수로 반올림합니다.</p>}<label>추가 사항 <small>선택</small><textarea maxLength={300} value={form.notes ?? ''} onChange={e => setForm({...form,notes:e.target.value})} placeholder="플레이 스타일 등" /></label>{form.fromMyProfile && <div className="profile-prefill"><p className="help">내 계정의 {club?.sport} 정보가 입력되었습니다. 저장하면 이 모임의 내 선수 정보로 등록됩니다.</p><button type="button" className="text-button" onClick={() => setForm({ ...blankMember })}>{isManager ? '다른 회원 입력' : '직접 입력'}</button></div>}{form.id && isManager && <label className="check-line"><input type="checkbox" disabled={Boolean(user && club?.members.find(m => m.id === form.id)?.active === false)} checked={form.active} onChange={e => setForm({...form,active:e.target.checked})}/> 활동 중인 회원</label>}</>}
      {error && <p className="error" role="alert">{error}</p>}{storageError && <p className="error" role="alert">{storageError}</p>}<button className="primary" type="submit">{dialog === 'club' ? '모임 만들기' : '저장하기'} <span>→</span></button></form></section></div>}
  </div>;
}
