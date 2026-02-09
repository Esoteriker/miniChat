import type { Chat, MessagePage, StreamEvent } from "./types";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

type TokenResponse = { accessToken: string };
type GenerationResponse = { generationId: string };

type RequestOptions = {
  method?: "GET" | "POST" | "PATCH" | "DELETE";
  token?: string | null;
  body?: unknown;
};

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = {
    Accept: "application/json"
  };

  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }

  if (options.token) {
    headers.Authorization = `Bearer ${options.token}`;
  }

  const response = await fetch(`${API_BASE}${path}`, {
    method: options.method ?? "GET",
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body)
  });

  if (!response.ok) {
    const message = await extractError(response);
    throw new Error(message);
  }

  if (response.status === 204) {
    return null as T;
  }

  return (await response.json()) as T;
}

async function extractError(response: Response): Promise<string> {
  try {
    const payload = await response.json();
    const message = payload?.error?.message || payload?.message;
    if (typeof message === "string" && message.trim()) {
      return message;
    }
  } catch {
    // ignore
  }
  return `Request failed (${response.status})`;
}

export const authApi = {
  async login(email: string, password: string): Promise<TokenResponse> {
    return request<TokenResponse>("/api/auth/login", {
      method: "POST",
      body: { email, password }
    });
  },

  async register(email: string, password: string): Promise<TokenResponse> {
    return request<TokenResponse>("/api/auth/register", {
      method: "POST",
      body: { email, password }
    });
  }
};

export const chatApi = {
  async list(token: string): Promise<Chat[]> {
    return request<Chat[]>("/api/chats", { token });
  },

  async create(token: string, title?: string): Promise<Chat> {
    return request<Chat>("/api/chats", {
      method: "POST",
      token,
      body: { title: title ?? null }
    });
  },

  async rename(token: string, chatId: string, title: string): Promise<Chat> {
    return request<Chat>(`/api/chats/${chatId}`, {
      method: "PATCH",
      token,
      body: { title }
    });
  },

  async remove(token: string, chatId: string): Promise<void> {
    return request<void>(`/api/chats/${chatId}`, {
      method: "DELETE",
      token
    });
  }
};

export const messageApi = {
  async list(token: string, chatId: string, limit = 100): Promise<MessagePage> {
    return request<MessagePage>(`/api/chats/${chatId}/messages?limit=${limit}`, { token });
  }
};

export const generationApi = {
  async create(token: string, chatId: string, userMessage: string): Promise<GenerationResponse> {
    return request<GenerationResponse>(`/api/chats/${chatId}/generations`, {
      method: "POST",
      token,
      body: { userMessage }
    });
  },

  async cancel(token: string, generationId: string): Promise<void> {
    return request<void>(`/api/generations/${generationId}/cancel`, {
      method: "POST",
      token
    });
  },

  async stream(
    token: string,
    generationId: string,
    onEvent: (event: StreamEvent) => void,
    signal?: AbortSignal
  ): Promise<void> {
    const response = await fetch(`${API_BASE}/api/generations/${generationId}/stream`, {
      method: "GET",
      headers: {
        Accept: "text/event-stream",
        Authorization: `Bearer ${token}`
      },
      signal
    });

    if (!response.ok || !response.body) {
      const message = await extractError(response);
      throw new Error(message);
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";

    while (true) {
      const { done, value } = await reader.read();
      if (done) {
        break;
      }

      buffer += decoder.decode(value, { stream: true }).replace(/\r/g, "");

      let blockEnd = buffer.indexOf("\n\n");
      while (blockEnd >= 0) {
        const block = buffer.slice(0, blockEnd).trim();
        buffer = buffer.slice(blockEnd + 2);

        if (block) {
          for (const line of block.split("\n")) {
            if (!line.startsWith("data:")) {
              continue;
            }

            const raw = line.slice(5).trim();
            if (!raw) {
              continue;
            }

            try {
              onEvent(JSON.parse(raw) as StreamEvent);
            } catch {
              // ignore malformed chunk
            }
          }
        }

        blockEnd = buffer.indexOf("\n\n");
      }
    }
  }
};
