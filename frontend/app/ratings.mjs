export const lolTiers = ['아이언', '브론즈', '실버', '골드', '플래티넘', '에메랄드', '다이아몬드', '마스터', '그랜드마스터', '챌린저'];
export const isLol = sportType => sportType === 'LEAGUE_OF_LEGENDS';
export const isTierRating = rating => Number.isInteger(rating) && rating >= 10 && rating <= 100 && rating % 10 === 0;

export function formatRating(rating, sportType, suffix = '점') {
  if (!Number.isFinite(rating) || rating < 1 || rating > 100) return '미등록';
  if (!isLol(sportType)) return `${rating / 10}${suffix}`;
  const tier = lolTiers[Math.max(0, Math.min(9, Math.round(rating / 10) - 1))];
  return isTierRating(rating) ? tier : `${tier} (환산)`;
}
