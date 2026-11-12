const steps = [
  {
    title: '시작하기',
    description: '시작 화면 → 로그인 또는 게스트 선택',
  },
  {
    title: '모임 만들기와 가입하기',
    description: '홈 화면 → 새 모임 만들기 → 종목 고르기\n초대 링크 → 모임 가입',
  },
  {
    title: '선수 명단 준비하기',
    description: '모임 → 회원 관리 → 선수 추가\nMY → 종목별 내 정보 저장',
  },
  {
    title: '팀 나누기',
    description: '모임 → 팀 나누기 → 참석자·팀 수 선택 → 결과 확인\n로그인: 추가 조건·AI 분석',
  },
  {
    title: '편성 결과 저장하기',
    description: '결과 확정하기 → 모임 → 이전 경기 기록',
  },
];

export default function UserGuide() {
  return <section className="guide-page" aria-labelledby="guide-title">
    <div className="heading">
      <div className="eyebrow blue">HOW TO USE TEAM UP</div>
      <h2 id="guide-title">앱 사용 설명서</h2>
      <p>모임 → 팀 편성 → 기록</p>
    </div>
    <ol className="guide-steps">
      {steps.map((step, index) => <li className="guide-step" key={step.title}>
        <span className="guide-index">{String(index + 1).padStart(2, '0')}</span>
        <div><h3>{step.title}</h3><p>{step.description}</p></div>
      </li>)}
    </ol>
    <div className="guide-note"><strong>게스트로 사용할 때</strong><p>이 브라우저에만 저장 · 초대·자연어 조건·AI 분석은 로그인 필요</p></div>
  </section>;
}
