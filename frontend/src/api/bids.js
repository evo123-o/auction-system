import apiClient from './axios';

export const bidsAPI = {
  place(itemId, amount) {
    return apiClient.post('/api/bids/place', { itemId, amount });
  },

  placeBidOnItem(itemId, amount) {
    return apiClient.post(`/api/items/${itemId}/bid`, { amount });
  },

  history(itemId) {
    return apiClient.get('/api/bids/history', { params: { itemId } });
  }
};
