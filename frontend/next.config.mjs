/** @type {import('next').NextConfig} */
const nextConfig = {
  /* config options here */
  cacheComponents: true,
  partialPrefetching: true,
  async headers() {
    return [{
      source: '/vote-push.js',
      headers: [{ key: 'Cache-Control', value: 'no-cache, no-store, must-revalidate' }],
    }];
  },
  async rewrites() {
    const backend = process.env.BACKEND_URL || 'http://127.0.0.1:8080';
    return ['/api/:path*', '/oauth2/:path*', '/login/oauth2/:path*'].map(source => ({
      source, destination: `${backend}${source}`,
    }));
  },
};

export default nextConfig;
