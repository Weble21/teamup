'use client';

import { useState } from 'react';
import Image from 'next/image';

const sample = [{ id: 'c1', name: '주말 농구 모임', sport: '농구', members: [
  ['p1', '김민수', '가드', 80], ['p2', '이서준', '포워드', 70], ['p3', '박지훈', '센터', 80], ['p4', '최유진', '가드', 60], ['p5', '정하늘', '포워드', 70], ['p6', '한지민', '가드', 70],
].map(([id, name, position, rating]) => ({ id, name, position, rating, active: true, age: '', notes: '' })), matches: [] }, { id: 'c2', name: '수요일 풋살', sport: '풋살', members: [], matches: [] }];
const uid = () => globalThis.crypto.randomUUID();
const average = (team) => team.length ? team.reduce((sum, p) => sum + p.rating, 0) / team.length : 0;
const blankMember = { name: '', position: '', rating: '', age: '', notes: '' };

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
  const [clubs, setClubs] = useState(sample);
  const [screen, setScreen] = useState('welcome');
  const [clubId, setClubId] = useState(null);
  const [query, setQuery] = useState('');
  const [dialog, setDialog] = useState(null);
  const [form, setForm] = useState({});
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [teamCount, setTeamCount] = useState(2);
  const [attendees, setAttendees] = useState([]);
  const [teams, setTeams] = useState([]);
  const [swap, setSwap] = useState(null);
  const [historyItem, setHistoryItem] = useState(null);
  const club = clubs.find(c => c.id === clubId);
  const activeMembers = club?.members.filter(m => m.active) ?? [];

  function updateClub(fn) { setClubs(old => old.map(c => c.id === clubId ? fn(c) : c)); }
  function openDialog(type, value = {}) { setForm(value); setError(''); setDialog(type); }
  function closeDialog() { setDialog(null); setError(''); }
  function saveClub(e) {
    e.preventDefault();
    if (!form.name?.trim() || !form.sport) return setError('모임 이름과 종목을 입력해 주세요.');
    setClubs(old => [...old, { id: uid(), name: form.name.trim(), sport: form.sport, members: [], matches: [] }]); closeDialog();
  }
  function saveMember(e) {
    e.preventDefault();
    const rating = Number(form.rating), age = form.age === '' ? null : Number(form.age);
    if (!form.name?.trim() || !form.position?.trim()) return setError('이름과 포지션을 입력해 주세요.');
    if (!Number.isInteger(rating) || rating < 1 || rating > 100) return setError('능력치는 1부터 100까지의 정수로 입력해 주세요.');
    if (age !== null && (!Number.isInteger(age) || age < 0 || age > 130)) return setError('나이를 확인해 주세요.');
    const member = { ...form, id: form.id ?? uid(), name: form.name.trim(), position: form.position.trim(), rating, age: age ?? '', notes: form.notes?.trim() ?? '', active: form.active ?? true };
    updateClub(c => ({ ...c, members: form.id ? c.members.map(m => m.id === form.id ? member : m) : [...c.members, member] })); closeDialog();
  }
  function setUnknownRating() {
    const rated = activeMembers.filter(m => m.id !== form.id && Number.isInteger(m.rating));
    if (!rated.length) return setError('기준이 되는 능력치가 없습니다. 직접 입력해 주세요.');
    setForm(f => ({ ...f, rating: Math.max(1, Math.round(rated.reduce((n, m) => n + m.rating, 0) / rated.length - 5)) })); setError('');
  }
  function startFormation() { setAttendees(activeMembers.map(m => m.id)); setTeamCount(2); setScreen('formation'); setNotice(''); }
  function generate() {
    const players = activeMembers.filter(p => attendees.includes(p.id));
    if (players.length < teamCount) return setNotice('각 팀에 한 명 이상 배정할 수 있도록 참석자를 선택해 주세요.');
    setTeams(assign(players, teamCount)); setSwap(null); setNotice(''); setScreen('result');
  }
  function swapMember(teamIndex, memberIndex) {
    if (!swap) { setSwap({ teamIndex, memberIndex }); return; }
    if (swap.teamIndex === teamIndex) { setSwap({ teamIndex, memberIndex }); return; }
    setTeams(old => { const next = old.map(t => [...t]); [next[swap.teamIndex][swap.memberIndex], next[teamIndex][memberIndex]] = [next[teamIndex][memberIndex], next[swap.teamIndex][swap.memberIndex]]; return next; }); setSwap(null);
  }
  function saveMatch() {
    const snapshot = teams.map(t => t.map(({ id, name, position, rating }) => ({ id, name, position, rating })));
    const match = { id: uid(), date: new Date().toISOString(), sport: club.sport, teams: snapshot };
    updateClub(c => ({ ...c, matches: [match, ...c.matches] })); setScreen('history'); setNotice('경기 기록은 현재 탭을 새로고침하면 사라집니다.');
  }
  const back = () => { setNotice(''); setScreen(({ clubs: 'welcome', detail: 'clubs', members: 'detail', formation: 'detail', result: 'formation', history: 'detail', match: 'history' })[screen] ?? 'clubs'); };
  const title = ({ detail: club?.name, members: '회원 관리', formation: '팀 나누기', result: '팀 편성 결과', history: '경기 기록', match: '경기 상세' })[screen];
  return <div className="shell">
    <aside className="desktop-brand"><div className="brand">TEAM <span>/</span> UP</div><div className="side-line"/><p>함께 뛰는 시간,<br/><strong>더 좋은 팀을 만듭니다.</strong></p><small>모임부터 경기 기록까지<br/>당신의 다음 경기를 준비하세요.</small><div className="side-footer">PLAY TOGETHER · PLAY BETTER</div></aside>
    <div className="app">
      {screen !== 'welcome' && <header className="header">{screen === 'clubs' ? <div className="brand">TEAM <span>/</span> UP</div> : <><button className="back" onClick={back} aria-label="뒤로 가기">‹</button><h1>{title}</h1></>}<span className="preview-chip">미리보기</span></header>}
      <main className={screen === 'welcome' ? 'welcome' : 'content'}>
        {screen === 'welcome' && <><section className="hero"><div className="red-line"/><div className="brand light">TEAM <span>/</span> UP</div><div className="hero-copy"><div className="eyebrow">MAKE YOUR GAME BETTER</div><h1>좋은 경기는<br/>균형 잡힌 팀에서<span>.</span></h1><p>모이고, 나누고, 함께 즐겨요.</p></div><Image className="logo" src="/teamup-logo.png" alt="TEAM UP 엠블럼" width={100} height={100} priority /></section><section className="intro"><div className="eyebrow blue">READY TO PLAY?</div><h2>우리의 다음 경기를 시작해요</h2><p>모임과 선수 명단을 한 곳에서 관리하세요.</p><button className="primary" onClick={() => setScreen('clubs')}>미리보기로 시작하기 <span>→</span></button><div className="preview-note">서버 연결 전 미리보기 · 새로고침 시 데이터 초기화</div></section></>}
        {screen === 'clubs' && <><div className="heading"><div className="eyebrow blue">YOUR COMMUNITY</div><h2>내 모임</h2><p>함께 뛰는 모임을 선택해 주세요</p></div><div className="stack">{clubs.map((c, i) => <button key={c.id} className={'club-card ' + (i === 0 ? 'featured' : '')} onClick={() => {setClubId(c.id); setScreen('detail');}}><div className="club-top"><span className="club-icon">{c.sport[0]}</span><span className="eyebrow">{c.sport.toUpperCase()} CLUB</span><span className="arrow">↗</span></div><strong>{c.name}</strong><small>{c.sport} <i>·</i> 등록 회원 {c.members.filter(m => m.active).length}명</small></button>)}<button className="outline-action" onClick={() => openDialog('club', { name: '', sport: '농구' })}>＋ &nbsp;새 모임 만들기</button></div></>}
        {screen === 'detail' && <><div className="meta">{club.sport} CLUB <span>/</span> 등록 회원 {activeMembers.length}명</div><div className="battle"><div className="eyebrow">READY FOR</div><h2>YOUR NEXT<br/>BATTLE<span>.</span></h2><p>오늘은 어떤 팀으로 뛸까요?</p><div className="battle-mark">✳</div></div><div className="section-heading"><div><div className="eyebrow blue">CLUB MENU</div><h2>무엇을 할까요?</h2></div><small>SELECT MENU</small></div><div className="stack">{[['01','팀 나누기','참석자를 선택하고 팀을 구성해요',startFormation],['02','회원 관리','선수 정보와 능력치를 관리해요',() => {setQuery('');setScreen('members');}],['03','이전 경기 기록','지난 팀 편성과 경기 결과를 확인해요',() => setScreen('history')]].map(([n,t,d,fn]) => <button className="menu-card" key={n} onClick={fn}><span className="number">{n}</span><span><strong>{t}</strong><small>{d}</small></span><span className="chevron">→</span></button>)}</div></>}
        {screen === 'members' && <><div className="meta">{club.name} <span>·</span> 활성 회원 {activeMembers.length}명</div><input className="search" placeholder="⌕  이름으로 검색" value={query} onChange={e => setQuery(e.target.value)} aria-label="회원 이름 검색"/><div className="section-heading"><div><div className="eyebrow blue">THE ROSTER</div><h2>선수 명단</h2></div><button className="text-button" onClick={() => openDialog('member', {...blankMember})}>＋ 회원 추가</button></div><div className="stack compact">{club.members.filter(m => m.name.includes(query)).map(m => <button key={m.id} className={'member-card ' + (!m.active ? 'inactive' : '')} onClick={() => openDialog('member', {...m})}><span className="avatar">{m.name[0]}</span><span className="member-name"><strong>{m.name} {!m.active && <em>비활성</em>}</strong><small>{m.position}</small></span><span className="rating">{m.rating}<small> / 100</small></span><span className="chevron">›</span></button>)}{!club.members.filter(m => m.name.includes(query)).length && <div className="empty">표시할 선수가 없습니다.</div>}</div></>}
        {screen === 'formation' && <><div className="meta">참석자와 편성 조건을 선택해 주세요</div><div className="section-heading"><div><div className="eyebrow blue">01 / TEAM SETUP</div><h2>팀 수</h2></div></div><div className="stepper"><button aria-label="팀 수 줄이기" disabled={teamCount <= 2} onClick={() => setTeamCount(n => n-1)}>−</button><strong>{teamCount} <small>팀</small></strong><button aria-label="팀 수 늘리기" disabled={teamCount >= 10} onClick={() => setTeamCount(n => n+1)}>＋</button></div><div className="section-heading"><div><div className="eyebrow blue">02 / SELECT PLAYERS</div><h2>참석자 선택</h2></div><span className="selected-count">{attendees.length}명 선택</span></div><div className="stack compact">{activeMembers.map(m => <label className="select-card" key={m.id}><input type="checkbox" checked={attendees.includes(m.id)} onChange={e => setAttendees(old => e.target.checked ? [...old,m.id] : old.filter(id => id !== m.id))}/><strong>{m.name}</strong><small>{m.position} · {m.rating}점</small></label>)}{!activeMembers.length && <div className="empty">회원 관리에서 선수를 먼저 추가해 주세요.</div>}</div><div className="section-heading"><div><div className="eyebrow blue">03 / CONDITIONS</div><h2>추가 조건 <small>선택</small></h2></div></div><div className="disabled-box">자연어 조건과 필수 제약은 서버 연동 후 제공됩니다.</div>{notice && <p className="error" role="alert">{notice}</p>}<button className="primary bottom-action" onClick={generate}>팀 나누기 <span>→</span></button><p className="help">미리보기는 능력치 중심의 간단한 편성으로, 최적해를 보장하지 않습니다.</p></>}
        {screen === 'result' && <><div className="meta">다른 팀의 선수를 차례로 선택해 교환하세요</div><div className="balance"><span>평균 능력치 차이</span><strong>{(Math.max(...teams.map(average))-Math.min(...teams.map(average))).toFixed(1)} <small>POINTS</small></strong></div><div className="stack">{teams.map((team, i) => <section className={'team-card tone-'+(i%2)} key={i}><div className="team-header"><div><div className="eyebrow">LINEUP {String(i+1).padStart(2,'0')}</div><h2>TEAM {String.fromCharCode(65+i)}</h2></div><div className="team-score"><strong>{average(team).toFixed(1)}</strong><small>평균 · 총점 {team.reduce((s,p)=>s+p.rating,0)}</small></div></div>{team.map((p,j) => <button className={'team-player '+(swap?.teamIndex===i && swap?.memberIndex===j?'chosen':'')} key={p.id} onClick={() => swapMember(i,j)}><strong>{p.name}</strong><span>{p.position}</span><b>{p.rating}점</b></button>)}</section>)}</div>{swap && <p className="help">교환할 다른 팀의 선수를 선택해 주세요.</p>}<div className="button-row"><button className="secondary" onClick={generate}>다시 나누기</button><button className="secondary" onClick={() => setSwap(null)}>선택 취소</button></div><button className="primary" onClick={saveMatch}>결과 확정하기 <span>→</span></button><p className="help">확정 결과는 이 탭에서만 보관됩니다.</p></>}
        {screen === 'history' && <><div className="meta">{club.name}의 지난 경기를 모았어요</div><div className="section-heading"><div><div className="eyebrow blue">MATCH ARCHIVE</div><h2>최근 경기</h2></div></div><div className="stack">{club.matches.length === 0 && <div className="empty">아직 저장된 경기 기록이 없습니다.<br/>첫 경기를 만들어 보세요.</div>}{club.matches.map(match => <button key={match.id} className="history-card" onClick={() => {setHistoryItem(match);setScreen('match');}}><small>{new Date(match.date).toLocaleDateString('ko-KR', {year:'numeric',month:'long',day:'numeric'})} · {match.sport}</small><strong>팀 편성 기록 <span>→</span></strong><small>참석 {match.teams.flat().length}명 · {match.teams.length}개 팀</small></button>)}</div></>}
        {screen === 'match' && historyItem && <><div className="meta">{new Date(historyItem.date).toLocaleDateString('ko-KR')} · {historyItem.sport}</div><div className="section-heading"><div><div className="eyebrow blue">MATCH LINEUP</div><h2>당시 팀 구성</h2></div></div><div className="stack">{historyItem.teams.map((team,i) => <section className="team-card" key={i}><div className="team-header"><h2>TEAM {String.fromCharCode(65+i)}</h2><div className="team-score"><strong>{average(team).toFixed(1)}</strong><small>평균 · 총점 {team.reduce((n,p)=>n+p.rating,0)}</small></div></div>{team.map(p => <div className="team-player" key={p.id}><strong>{p.name}</strong><span>{p.position}</span><b>{p.rating}점</b></div>)}</section>)}</div><p className="help">기록 당시의 회원 정보입니다.</p></>}
      </main>
      {screen !== 'welcome' && <nav className="bottom-nav" aria-label="하단 메뉴"><button className={['clubs','detail','members','formation','result'].includes(screen)?'active':''} onClick={() => setScreen('clubs')}><span>◉</span> 모임</button><button className={['history','match'].includes(screen)?'active':''} onClick={() => club ? setScreen('history') : setNotice('모임을 먼저 선택해 주세요.')}><span>◷</span> 경기 기록</button><button onClick={() => setNotice('로그인 기능은 서버 연동 후 제공됩니다.')}><span>○</span> MY</button></nav>}
      {notice && screen !== 'formation' && <div className="toast" role="status" onClick={() => setNotice('')}>{notice} <span>×</span></div>}
    </div>
    {dialog && <div className="overlay" onClick={closeDialog}><section className="modal" role="dialog" aria-modal="true" aria-labelledby="dialog-title" onClick={e => e.stopPropagation()}><div className="modal-heading"><div><div className="eyebrow blue">{dialog === 'club' ? 'NEW CLUB' : 'PLAYER PROFILE'}</div><h2 id="dialog-title">{dialog === 'club' ? '새 모임 만들기' : form.id ? '회원 정보 수정' : '새 회원 추가'}</h2></div><button className="close" onClick={closeDialog} aria-label="닫기">×</button></div><form onSubmit={dialog === 'club' ? saveClub : saveMember}>
      <label>이름 <b>*</b><input maxLength={40} value={form.name ?? ''} onChange={e => setForm({...form,name:e.target.value})} placeholder={dialog === 'club'?'예: 주말 농구 모임':'선수 이름'} /></label>
      {dialog === 'club' ? <label>운동 종목 <b>*</b><select value={form.sport ?? '농구'} onChange={e => setForm({...form,sport:e.target.value})}>{['농구','풋살','축구','배구','야구','리그 오브 레전드','기타'].map(s=><option key={s}>{s}</option>)}</select></label> : <><label>포지션 <b>*</b><input maxLength={30} value={form.position ?? ''} onChange={e => setForm({...form,position:e.target.value})} placeholder="예: 가드" /></label><div className="form-row"><label>능력치 <b>*</b><input type="number" min="1" max="100" value={form.rating ?? ''} onChange={e => setForm({...form,rating:e.target.value})} placeholder="1 ~ 100" /></label><label>나이 <small>선택</small><input type="number" min="0" max="130" value={form.age ?? ''} onChange={e => setForm({...form,age:e.target.value})} placeholder="입력 안 함" /></label></div><button type="button" className="text-button estimate" onClick={setUnknownRating}>실력을 잘 모르겠어요 · 모임 평균 -5점 적용</button><p className="help">활성 회원의 평균을 반올림하고 1점 이상으로 보정한 미리보기 기준입니다.</p><label>추가 사항 <small>선택</small><textarea maxLength={300} value={form.notes ?? ''} onChange={e => setForm({...form,notes:e.target.value})} placeholder="플레이 스타일 등" /></label>{form.id && <label className="check-line"><input type="checkbox" checked={form.active} onChange={e => setForm({...form,active:e.target.checked})}/> 활동 중인 회원</label>}</>}
      {error && <p className="error" role="alert">{error}</p>}<button className="primary" type="submit">{dialog === 'club' ? '모임 만들기' : '저장하기'} <span>→</span></button></form></section></div>}
  </div>;
}
