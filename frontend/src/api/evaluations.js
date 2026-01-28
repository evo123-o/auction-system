import apiClient from './axios';

export const evaluationsAPI = {
  // 获取订单的评价
  getByOrderId(orderId) {
    return apiClient.get(`/api/evaluations/order/${orderId}`);
  },

  // 获取我的所有评价
  myEvaluations() {
    return apiClient.get('/api/evaluations/my');
  },

  // 创建评价
  create(data) {
    return apiClient.post('/api/evaluations', data);
  },

  // 检查是否已评价
  checkReviewed(orderId) {
    return apiClient.get(`/api/evaluations/check/${orderId}`);
  }
};
