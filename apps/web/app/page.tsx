import Link from "next/link";

export default function HomePage() {
  return (
    <main style={{ padding: 24 }}>
      <h1>MiniChat Scaffold</h1>
      <p>Milestone 1 initialized. Use routes below:</p>
      <ul>
        <li>
          <Link href="/login">/login</Link>
        </li>
        <li>
          <Link href="/chat">/chat</Link>
        </li>
      </ul>
    </main>
  );
}
