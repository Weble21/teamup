import { formatRating, isLol, isTierRating, lolTiers } from './ratings.mjs';

export default function RatingField({ sportType, rating, onChange }) {
  if (isLol(sportType)) return <>
    <label>티어 <b>*</b><select value={rating} onChange={e => onChange(Number(e.target.value))}>
      {!isTierRating(rating) && <option value={rating}>{formatRating(rating, sportType)} · 기존 값 유지</option>}
      {lolTiers.map((tier, index) => <option key={tier} value={(index + 1) * 10}>{tier}</option>)}
    </select></label>
    {!isTierRating(rating) && <p className="help">기존 능력치를 가까운 티어로 환산해 표시합니다. 새 티어를 선택하기 전까지 저장값은 유지됩니다.</p>}
  </>;
  return <>
    <label className="rating-field">능력치 <b>*</b><span className="rating-value">{rating / 10} <small>/ 10</small></span><input type="range" min="1" max="10" step="1" value={Math.max(1, Math.min(10, Math.round(rating / 10)))} onChange={e => onChange(Number(e.target.value) * 10)} aria-valuetext={rating / 10 + '점'} /><span className="rating-scale"><span>1</span><span>10</span></span></label>
    {rating % 10 !== 0 && <p className="help">기존 능력치가 표시됩니다. 바를 조절하면 1~10의 정수로 변경됩니다.</p>}
  </>;
}
