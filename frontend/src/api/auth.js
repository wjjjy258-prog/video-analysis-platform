import http from "./http";

export const login = async (payload) => {
  const { data } = await http.post("/auth/login", payload, { silentError: true });
  return data;
};

export const register = async (payload) => {
  const { data } = await http.post("/auth/register", payload, { silentError: true });
  return data;
};

export const me = async () => {
  const { data } = await http.get("/auth/me", { silentError: true });
  return data;
};

export const logout = async () => {
  const { data } = await http.post("/auth/logout", null, { silentError: true });
  return data;
};
