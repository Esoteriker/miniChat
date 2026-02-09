import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "MiniChat",
  description: "ChatGPT-like local product scaffold"
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
