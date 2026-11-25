'use client';

import RatingField from './RatingField';
import { sportCode } from './api';

export default function ClubOwnerProfileFields({ sport, profile, onChange }) {
  return <section className="club-owner-profile" aria-label="모임장 선수 정보">
    <h3>모임장 선수 정보</h3>
    <p className="help">모임을 만들면 입력한 정보가 선수 명단에 등록됩니다.</p>
    <label>선수 이름 <b>*</b><input required maxLength={100} value={profile.name} onChange={e => onChange({ ...profile, name: e.target.value })} placeholder="이름 또는 별명" /></label>
    <label>포지션 <b>*</b><input required maxLength={50} value={profile.position} onChange={e => onChange({ ...profile, position: e.target.value })} placeholder="예: 가드" /></label>
    <RatingField sportType={sportCode(sport)} rating={profile.rating} onChange={rating => onChange({ ...profile, rating })} />
    <label>나이 <small>선택</small><input type="number" min="0" max="130" value={profile.age} onChange={e => onChange({ ...profile, age: e.target.value })} /></label>
    <label>추가 사항 <small>선택</small><textarea maxLength={1000} value={profile.notes} onChange={e => onChange({ ...profile, notes: e.target.value })} /></label>
  </section>;
}
