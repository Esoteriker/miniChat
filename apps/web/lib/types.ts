export type Chat = {
  id: string;
  title: string;
  createdAt: string;
  updatedAt: string;
};

export type Message = {
  id: string;
  role: string;
  content: string;
  createdAt: string;
};

export type MessagePage = {
  items: Message[];
  nextCursor: string | null;
};

export type StreamEvent =
  | { type: "delta"; delta: string }
  | { type: "usage"; inputTokens: number; outputTokens: number }
  | { type: "error"; code: string; message: string }
  | { type: "done" };
