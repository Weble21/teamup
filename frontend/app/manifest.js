export default function manifest() {
  return {
    id: '/',
    name: 'TEAM UP',
    short_name: 'TEAM UP',
    description: '함께 뛰는 시간, 균형 잡힌 팀',
    lang: 'ko',
    start_url: '/',
    scope: '/',
    display: 'standalone',
    background_color: '#f5f6f8',
    theme_color: '#122446',
    icons: [
      { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'any' },
      { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
    ],
  };
}
