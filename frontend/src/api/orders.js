import apiClient from './axios';

export const ordersAPI = {
  // 获取订单详情
  getById(id) {
    return apiClient.get(`/api/orders/${id}`);
  },

  // 获取我的订单（作为买家）
  myBuyerOrders(params) {
    return apiClient.get('/api/orders/my/buyer', { params });
  },

  // 获取我的订单（作为卖家）
  mySellerOrders(params) {
    return apiClient.get('/api/orders/my/seller', { params });
  },

  // 管理员获取所有订单
  adminAllOrders(params) {
    return apiClient.get('/api/orders/admin/all', { params });
  },

  // 支付订单
  pay(id) {
    return apiClient.post(`/api/orders/pay/${id}`);
  },

  // 发货
  ship(id) {
    return apiClient.post(`/api/orders/ship/${id}`);
  },

  // 确认收货
  receive(id) {
    return apiClient.post(`/api/orders/receive/${id}`);
  },

  // 获取订单凭证
  getReceipt(id) {
    return apiClient.get(`/api/orders/${id}/receipt`);
  }
};
