import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "NeuroFlow",
  description: "Event-driven observability & MLOps platform",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body className="min-h-screen bg-canvas text-ink antialiased">{children}</body>
    </html>
  );
}
