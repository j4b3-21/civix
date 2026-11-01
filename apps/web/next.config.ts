import type { NextConfig } from "next";

/**
 * The browser always calls the API same-origin (`/api/v1/*`) so that the
 * httpOnly auth cookies stay first-party. Next forwards those requests to
 * Spring. Note: with `output: "standalone"` rewrites are resolved at build
 * time, so API_PROXY_TARGET must be set when running `next build`
 * (the Dockerfile exposes it as a build argument).
 */
const apiProxyTarget = process.env.API_PROXY_TARGET ?? "http://localhost:8080";

const securityHeaders = [
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
];

const nextConfig: NextConfig = {
  output: "standalone",
  reactStrictMode: true,
  poweredByHeader: false,
  // Response compression would buffer the proxied Server-Sent Events stream
  // (/api/v1/agent/tasks/{id}/events). Compress at the edge proxy instead.
  compress: false,
  async rewrites() {
    return [
      {
        source: "/api/v1/:path*",
        destination: `${apiProxyTarget}/api/v1/:path*`,
      },
    ];
  },
  async headers() {
    return [{ source: "/:path*", headers: securityHeaders }];
  },
};

export default nextConfig;
