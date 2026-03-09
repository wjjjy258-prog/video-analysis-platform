import { computed, ref } from "vue";

const TOKEN_KEY = "video-analysis-platform:auth-token";
const USER_KEY = "video-analysis-platform:auth-user";

const getStoredToken = () => {
  if (typeof window === "undefined") {
    return "";
  }
  return String(window.localStorage.getItem(TOKEN_KEY) || "").trim();
};

const getStoredUser = () => {
  if (typeof window === "undefined") {
    return null;
  }
  try {
    const raw = window.localStorage.getItem(USER_KEY);
    if (!raw) {
      return null;
    }
    return JSON.parse(raw);
  } catch {
    return null;
  }
};

const token = ref(getStoredToken());
const currentUser = ref(getStoredUser());

const persist = () => {
  if (typeof window === "undefined") {
    return;
  }
  if (token.value) {
    window.localStorage.setItem(TOKEN_KEY, token.value);
  } else {
    window.localStorage.removeItem(TOKEN_KEY);
  }
  if (currentUser.value) {
    window.localStorage.setItem(USER_KEY, JSON.stringify(currentUser.value));
  } else {
    window.localStorage.removeItem(USER_KEY);
  }
};

export const hasAuthToken = () => Boolean(token.value);

export const useAuth = () => {
  const isAuthenticated = computed(() => Boolean(token.value));

  const setAuth = (payload) => {
    token.value = String(payload?.token || "").trim();
    currentUser.value = payload?.user || null;
    persist();
  };

  const updateUser = (user) => {
    currentUser.value = user || null;
    persist();
  };

  const clearAuth = () => {
    token.value = "";
    currentUser.value = null;
    persist();
  };

  return {
    token,
    currentUser,
    isAuthenticated,
    setAuth,
    updateUser,
    clearAuth
  };
};
