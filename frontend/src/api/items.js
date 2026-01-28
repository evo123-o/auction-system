import apiClient from './axios';

export const itemsAPI = {
  list(params) {
    return apiClient.get('/api/items', { params });
  },

  getById(id) {
    return apiClient.get(`/api/items/${id}`);
  },

  create(data) {
    return apiClient.post('/api/items', data);
  },

  update(id, data) {
    return apiClient.put(`/api/items/${id}`, data);
  },

  delete(id) {
    return apiClient.delete(`/api/items/${id}`);
  },

  uploadImage(id, file) {
    const formData = new FormData();
    formData.append('file', file);
    return apiClient.post(`/api/items/${id}/image`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    });
  }
};
