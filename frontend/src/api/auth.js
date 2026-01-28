import apiClient from './axios';

export const authAPI = {
  login(username, password) {
    return apiClient.post('/api/auth/login', { username, password });
  },

  logout(refreshToken) {
    const params = refreshToken ? { refreshToken } : {};
    return apiClient.post('/api/auth/logout', null, { params });
  },

  refresh(refreshToken) {
    return apiClient.post('/api/auth/refresh', null, { params: { refreshToken } });
  }
};
