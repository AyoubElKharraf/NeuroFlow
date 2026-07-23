/** @type {import('next').NextConfig} */
const path = require("path");

const nextConfig = {
  reactStrictMode: true,
  outputFileTracingRoot: path.join(__dirname),
  env: {
    NEXT_PUBLIC_API_URL: process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080",
    NEXT_PUBLIC_ANALYTICS_URL:
      process.env.NEXT_PUBLIC_ANALYTICS_URL || "http://localhost:8001",
  },
};

module.exports = nextConfig;
