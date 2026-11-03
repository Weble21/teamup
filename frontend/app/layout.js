import './globals.css';

export const metadata = {
  title: 'TEAM / UP — 함께 뛰는 시간',
  description: '모임의 선수를 관리하고 균형 잡힌 팀을 구성하세요.',
  manifest: '/manifest.webmanifest',
  appleWebApp: { capable: true, title: 'TEAM UP', statusBarStyle: 'default' },
};
export const viewport = { themeColor: '#122446', width: 'device-width', initialScale: 1 };

export default function RootLayout({ children }) {
  return <html lang="ko"><body>{children}</body></html>;
}
