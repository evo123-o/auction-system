import apiClient from './axios';

export const depositsAPI = {
  // 初始化保证金（准备缴纳）
  init(itemId) {
    return apiClient.post(`/api/deposits/init/${itemId}`);
  },

  // 模拟支付保证金
  pay(depositId) {
    return apiClient.post(`/api/deposits/pay/${depositId}`);
  },

  // 检查是否有资格出价
  checkStatus(itemId) {
    return apiClient.get('/api/deposits/status', { params: { itemId } });
  },

  // 获取我的所有保证金记录
  myDeposits() {
    return apiClient.get('/api/deposits/my');
  },

  // 获取保证金详情
  getById(depositId) {
    return apiClient.get(`/api/deposits/${depositId}`);
  }
};
