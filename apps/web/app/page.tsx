import Link from "next/link";

export default function HomePage() {
  return (
    <main className="home-shell">
      <section className="home-card">
        <p className="eyebrow">MiniChat</p>
        <h1>Local Chat Stack</h1>
        <p>Multi-service ChatGPT-like system with Spring + FastAPI + Go worker.</p>
        <div className="home-actions">
          <Link href="/login">Go to Login</Link>
          <Link href="/chat">Go to Chat</Link>
        </div>
      </section>
    </main>
  );
}
