import { useState, useCallback } from 'react';

const SESSION_KEY = 'ai_session_id';
const SESSION_EXPIRY_KEY = 'ai_session_expiry';
const SESSION_TTL_MS = 30 * 60 * 1000; // 30 分钟

export function useSession() {
  const [sessionId, setSessionIdState] = useState<string | null>(() => {
    try {
      const stored = localStorage.getItem(SESSION_KEY);
      const expiry = localStorage.getItem(SESSION_EXPIRY_KEY);
      if (stored && expiry && Date.now() < Number(expiry)) {
        return stored;
      }
      // 过期清理
      localStorage.removeItem(SESSION_KEY);
      localStorage.removeItem(SESSION_EXPIRY_KEY);
    } catch { /* localStorage 不可用时忽略 */ }
    return null;
  });

  const saveSessionId = useCallback((id: string) => {
    setSessionIdState(id);
    try {
      localStorage.setItem(SESSION_KEY, id);
      localStorage.setItem(SESSION_EXPIRY_KEY, String(Date.now() + SESSION_TTL_MS));
    } catch { /* localStorage 不可用时忽略 */ }
  }, []);

  const clearSession = useCallback(() => {
    setSessionIdState(null);
    try {
      localStorage.removeItem(SESSION_KEY);
      localStorage.removeItem(SESSION_EXPIRY_KEY);
    } catch { /* ignore */ }
  }, []);

  return { sessionId, saveSessionId, clearSession };
}
