import type { User } from "./types";

const TOKEN_KEY = "proofdrop.token";
const USER_KEY = "proofdrop.user";

// localStorage can throw (private mode, blocked storage); the app must still work for the tab's lifetime.
let memoryToken: string | null = null;
let memoryUser: User | null = null;

function read(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function write(key: string, value: string | null) {
  try {
    if (value === null) localStorage.removeItem(key);
    else localStorage.setItem(key, value);
  } catch {
    /* ignore */
  }
}

export const session = {
  token(): string | null {
    return memoryToken ?? read(TOKEN_KEY);
  },
  user(): User | null {
    if (memoryUser) return memoryUser;
    const raw = read(USER_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw) as User;
    } catch {
      return null;
    }
  },
  save(token: string, user: User) {
    memoryToken = token;
    memoryUser = user;
    write(TOKEN_KEY, token);
    write(USER_KEY, JSON.stringify(user));
  },
  clear() {
    memoryToken = null;
    memoryUser = null;
    write(TOKEN_KEY, null);
    write(USER_KEY, null);
  },
};
