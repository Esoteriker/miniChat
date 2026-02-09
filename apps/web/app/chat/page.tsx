"use client";

import { useRouter } from "next/navigation";
import { useEffect, useReducer, useRef } from "react";
import { chatApi, generationApi, messageApi } from "@/lib/api";
import type { Chat, Message, StreamEvent } from "@/lib/types";

const TOKEN_KEY = "minichat_access_token";

type State = {
  token: string | null;
  chats: Chat[];
  activeChatId: string | null;
  messages: Message[];
  input: string;
  isLoading: boolean;
  isStreaming: boolean;
  generationId: string | null;
  stopped: boolean;
  error: string | null;
};

type Action =
  | { type: "setToken"; token: string | null }
  | { type: "setChats"; chats: Chat[] }
  | { type: "upsertChat"; chat: Chat }
  | { type: "removeChat"; chatId: string }
  | { type: "setActiveChat"; chatId: string | null }
  | { type: "setMessages"; messages: Message[] }
  | { type: "appendMessages"; messages: Message[] }
  | { type: "updateAssistantDraft"; id: string; delta: string }
  | { type: "setInput"; input: string }
  | { type: "setLoading"; isLoading: boolean }
  | { type: "streamStart"; generationId: string }
  | { type: "streamDone" }
  | { type: "streamStopped" }
  | { type: "setError"; error: string | null };

const initialState: State = {
  token: null,
  chats: [],
  activeChatId: null,
  messages: [],
  input: "",
  isLoading: false,
  isStreaming: false,
  generationId: null,
  stopped: false,
  error: null
};

function reducer(state: State, action: Action): State {
  switch (action.type) {
    case "setToken":
      return { ...state, token: action.token };
    case "setChats":
      return { ...state, chats: action.chats };
    case "upsertChat": {
      const others = state.chats.filter((chat) => chat.id !== action.chat.id);
      return { ...state, chats: [action.chat, ...others] };
    }
    case "removeChat":
      return { ...state, chats: state.chats.filter((chat) => chat.id !== action.chatId) };
    case "setActiveChat":
      return { ...state, activeChatId: action.chatId };
    case "setMessages":
      return { ...state, messages: action.messages };
    case "appendMessages":
      return { ...state, messages: [...state.messages, ...action.messages] };
    case "updateAssistantDraft":
      return {
        ...state,
        messages: state.messages.map((msg) =>
          msg.id === action.id ? { ...msg, content: msg.content + action.delta } : msg
        )
      };
    case "setInput":
      return { ...state, input: action.input };
    case "setLoading":
      return { ...state, isLoading: action.isLoading };
    case "streamStart":
      return {
        ...state,
        isStreaming: true,
        generationId: action.generationId,
        stopped: false,
        error: null
      };
    case "streamDone":
      return { ...state, isStreaming: false, generationId: null };
    case "streamStopped":
      return { ...state, isStreaming: false, generationId: null, stopped: true };
    case "setError":
      return { ...state, error: action.error };
    default:
      return state;
  }
}

export default function ChatPage() {
  const router = useRouter();
  const [state, dispatch] = useReducer(reducer, initialState);
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    const token = localStorage.getItem(TOKEN_KEY);
    if (!token) {
      router.replace("/login");
      return;
    }
    dispatch({ type: "setToken", token });
  }, [router]);

  useEffect(() => {
    if (!state.token) {
      return;
    }

    void (async () => {
      try {
        dispatch({ type: "setLoading", isLoading: true });
        const chats = await chatApi.list(state.token as string);
        dispatch({ type: "setChats", chats });
        if (chats.length > 0) {
          dispatch({ type: "setActiveChat", chatId: chats[0].id });
        }
      } catch (err) {
        dispatch({ type: "setError", error: err instanceof Error ? err.message : "Failed to load chats" });
      } finally {
        dispatch({ type: "setLoading", isLoading: false });
      }
    })();
  }, [state.token]);

  useEffect(() => {
    if (!state.token || !state.activeChatId) {
      dispatch({ type: "setMessages", messages: [] });
      return;
    }

    void loadMessages(state.token, state.activeChatId);
  }, [state.token, state.activeChatId]);

  async function loadMessages(token: string, chatId: string) {
    try {
      dispatch({ type: "setLoading", isLoading: true });
      const page = await messageApi.list(token, chatId, 200);
      dispatch({ type: "setMessages", messages: page.items });
    } catch (err) {
      dispatch({ type: "setError", error: err instanceof Error ? err.message : "Failed to load messages" });
    } finally {
      dispatch({ type: "setLoading", isLoading: false });
    }
  }

  async function createChat() {
    if (!state.token) {
      return;
    }

    try {
      const chat = await chatApi.create(state.token, "New Chat");
      dispatch({ type: "upsertChat", chat });
      dispatch({ type: "setActiveChat", chatId: chat.id });
      dispatch({ type: "setMessages", messages: [] });
    } catch (err) {
      dispatch({ type: "setError", error: err instanceof Error ? err.message : "Failed to create chat" });
    }
  }

  async function renameChat(chat: Chat) {
    if (!state.token) {
      return;
    }
    const nextTitle = window.prompt("Rename chat", chat.title);
    if (!nextTitle || !nextTitle.trim()) {
      return;
    }

    try {
      const updated = await chatApi.rename(state.token, chat.id, nextTitle.trim());
      dispatch({ type: "upsertChat", chat: updated });
    } catch (err) {
      dispatch({ type: "setError", error: err instanceof Error ? err.message : "Failed to rename chat" });
    }
  }

  async function deleteChat(chat: Chat) {
    if (!state.token) {
      return;
    }

    const confirmed = window.confirm(`Delete chat \"${chat.title}\"?`);
    if (!confirmed) {
      return;
    }

    try {
      await chatApi.remove(state.token, chat.id);
      dispatch({ type: "removeChat", chatId: chat.id });

      if (state.activeChatId === chat.id) {
        const remaining = state.chats.filter((item) => item.id !== chat.id);
        const nextActive = remaining.length > 0 ? remaining[0].id : null;
        dispatch({ type: "setActiveChat", chatId: nextActive });
      }
    } catch (err) {
      dispatch({ type: "setError", error: err instanceof Error ? err.message : "Failed to delete chat" });
    }
  }

  async function sendMessage() {
    if (!state.token || state.isStreaming) {
      return;
    }

    const userInput = state.input.trim();
    if (!userInput) {
      return;
    }

    let chatId = state.activeChatId;
    if (!chatId) {
      const newChat = await chatApi.create(state.token, "New Chat");
      dispatch({ type: "upsertChat", chat: newChat });
      dispatch({ type: "setActiveChat", chatId: newChat.id });
      chatId = newChat.id;
    }

    const now = new Date().toISOString();
    const userDraftId = `draft-user-${Date.now()}`;
    const assistantDraftId = `draft-assistant-${Date.now()}`;

    dispatch({
      type: "appendMessages",
      messages: [
        { id: userDraftId, role: "user", content: userInput, createdAt: now },
        { id: assistantDraftId, role: "assistant", content: "", createdAt: now }
      ]
    });
    dispatch({ type: "setInput", input: "" });

    try {
      const createResponse = await generationApi.create(state.token, chatId, userInput);
      dispatch({ type: "streamStart", generationId: createResponse.generationId });

      abortRef.current = new AbortController();
      await generationApi.stream(
        state.token,
        createResponse.generationId,
        (event: StreamEvent) => {
          if (event.type === "delta") {
            dispatch({ type: "updateAssistantDraft", id: assistantDraftId, delta: event.delta });
            return;
          }
          if (event.type === "error") {
            if (event.code === "canceled") {
              dispatch({ type: "streamStopped" });
              return;
            }
            dispatch({ type: "setError", error: event.message });
          }
        },
        abortRef.current.signal
      );

      dispatch({ type: "streamDone" });
      await loadMessages(state.token, chatId);
      const refreshed = await chatApi.list(state.token);
      dispatch({ type: "setChats", chats: refreshed });
    } catch (err) {
      if ((err as Error).name === "AbortError") {
        return;
      }
      dispatch({ type: "streamDone" });
      dispatch({ type: "setError", error: err instanceof Error ? err.message : "Stream failed" });
    } finally {
      abortRef.current = null;
    }
  }

  async function stopStreaming() {
    if (!state.token || !state.generationId) {
      return;
    }

    try {
      await generationApi.cancel(state.token, state.generationId);
    } catch {
      // ignore cancel API race
    } finally {
      abortRef.current?.abort();
      abortRef.current = null;
      dispatch({ type: "streamStopped" });
    }
  }

  function logout() {
    localStorage.removeItem(TOKEN_KEY);
    router.push("/login");
  }

  const activeChat = state.chats.find((chat) => chat.id === state.activeChatId) ?? null;

  return (
    <main className="chat-shell">
      <aside className="chat-sidebar">
        <div className="sidebar-header">
          <h2>Conversations</h2>
          <button onClick={() => void createChat()}>New</button>
        </div>

        <div className="chat-list">
          {state.chats.map((chat) => (
            <div
              key={chat.id}
              className={`chat-item ${state.activeChatId === chat.id ? "active" : ""}`}
              onClick={() => dispatch({ type: "setActiveChat", chatId: chat.id })}
              role="button"
              tabIndex={0}
            >
              <div className="chat-item-title">{chat.title}</div>
              <div className="chat-item-actions">
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    void renameChat(chat);
                  }}
                >
                  Rename
                </button>
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    void deleteChat(chat);
                  }}
                >
                  Delete
                </button>
              </div>
            </div>
          ))}
        </div>
      </aside>

      <section className="chat-main">
        <header className="chat-main-header">
          <div>
            <p className="eyebrow">MiniChat</p>
            <h1>{activeChat?.title ?? "Select or create a chat"}</h1>
          </div>
          <button onClick={logout}>Logout</button>
        </header>

        <div className="message-list">
          {state.messages.map((message) => (
            <article key={message.id} className={`message-bubble ${message.role === "user" ? "user" : "assistant"}`}>
              <p className="message-role">{message.role}</p>
              <p>{message.content || "..."}</p>
            </article>
          ))}
          {!state.isLoading && state.messages.length === 0 && (
            <p className="empty-state">Start with a message. The assistant response will stream token by token.</p>
          )}
        </div>

        <footer className="composer">
          <textarea
            value={state.input}
            onChange={(e) => dispatch({ type: "setInput", input: e.target.value })}
            placeholder="Type your message..."
            rows={3}
            disabled={state.isStreaming}
          />
          <div className="composer-actions">
            <button onClick={() => void sendMessage()} disabled={state.isStreaming || !state.input.trim()}>
              Send
            </button>
            <button onClick={() => void stopStreaming()} disabled={!state.isStreaming}>
              Stop
            </button>
          </div>
          {state.stopped && <p className="status-text">已停止</p>}
          {state.error && <p className="error-text">{state.error}</p>}
        </footer>
      </section>
    </main>
  );
}
