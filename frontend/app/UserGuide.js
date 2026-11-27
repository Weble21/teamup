const steps = [
  {
    title: '모임 만들기',
    description: '카카오 또는 네이버로 로그인하세요. 내 모임에서 ‘새 모임 만들기’를 누르고 이름과 종목을 정하세요.',
  },
  {
    title: '선수 명단 준비하기',
    description: '모임장은 ‘회원 관리 → 회원 추가’에서 선수를 등록하거나 모임 화면의 초대 링크를 공유할 수 있어요.',
  },
  {
    title: '초대받은 모임에 참여하기',
    description: '초대 링크로 로그인한 뒤 명단에서 내 이름을 고르세요. 이름이 없으면 ‘명단에 없어요 · 내 정보 등록’을 누르세요. 가입 후 ‘운동 일정 투표’에서 참석 여부를 알릴 수 있어요.',
  },
  {
    title: '팀 나누기',
    description: '모임의 ‘빠른 팀 편성’에서 팀 수와 참석자를 고르고 ‘팀 나누기’를 누르세요. 예를 들어 6명을 2팀으로 나누면 3명씩 편성돼요.',
  },
  {
    title: '편성 결과 저장하기',
    description: '선수를 바꾸려면 서로 다른 팀의 두 명을 차례로 누르세요. ‘결과 확정하기’를 누르면 ‘이전 경기 기록’에서 명단을 다시 볼 수 있어요.',
  },
];

export default function UserGuide() {
  return <section className="guide-page" aria-labelledby="guide-title">
    <div className="heading">
      <div className="eyebrow blue">HOW TO USE TEAM UP</div>
      <h2 id="guide-title">앱 사용 설명서</h2>
      <p>모임장은 선수 명단을 준비하고, 초대받은 모임원은 내 정보를 등록해요.</p>
    </div>
    <ol className="guide-steps">
      {steps.map((step, index) => <li className="guide-step" key={step.title}>
        <span className="guide-index">{String(index + 1).padStart(2, '0')}</span>
        <div><h3>{step.title}</h3><p>{step.description}</p></div>
      </li>)}
    </ol>
    <div className="guide-note"><strong>게스트로 연습할 때</strong><p>모임과 경기 기록은 지금 쓰는 브라우저에만 저장돼요. 나중에 로그인해도 계정으로 옮겨지지 않아요.</p></div>
  </section>;
}
