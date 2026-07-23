import type { Config } from "tailwindcss";

const config: Config = {
  darkMode: "class",
  content: ["./src/**/*.{js,ts,jsx,tsx,mdx}"],
  theme: {
    extend: {
      colors: {
        canvas: "#070b14",
        panel: "#0d1424",
        elevated: "#121a2b",
        line: "#1e293b",
        ink: "#e8eef9",
        mute: "#94a3b8",
        accent: "#22d3ee",
        accent2: "#38bdf8",
        warn: "#fbbf24",
        danger: "#f87171",
        ok: "#34d399",
      },
      fontFamily: {
        sans: ["var(--font-sans)", "Segoe UI", "sans-serif"],
        mono: ["var(--font-mono)", "ui-monospace", "monospace"],
        display: ["var(--font-display)", "Georgia", "serif"],
      },
      boxShadow: {
        glow: "0 0 0 1px rgba(34, 211, 238, 0.12), 0 10px 40px rgba(0, 0, 0, 0.35)",
      },
      keyframes: {
        pulseDot: {
          "0%, 100%": { opacity: "1", transform: "scale(1)" },
          "50%": { opacity: "0.45", transform: "scale(0.85)" },
        },
        fadeUp: {
          from: { opacity: "0", transform: "translateY(8px)" },
          to: { opacity: "1", transform: "translateY(0)" },
        },
      },
      animation: {
        pulseDot: "pulseDot 1.6s ease-in-out infinite",
        fadeUp: "fadeUp 420ms ease-out both",
      },
    },
  },
  plugins: [],
};

export default config;
