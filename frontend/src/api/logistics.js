import apiClient from './axios';

export const logisticsAPI = {
  // 获取订单物流信息
  getByOrderId(orderId) {
    return apiClient.get(`/api/logistics/${orderId}`);
  },

  // 保存或更新物流信息
  saveOrUpdate(orderId, data) {
    return apiClient.post(`/api/logistics/${orderId}`, data);
  }
};
