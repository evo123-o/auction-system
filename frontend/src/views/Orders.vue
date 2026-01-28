<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import { ordersAPI } from '../api/orders';

const router = useRouter();
const authStore = useAuthStore();

const orders = ref([]);
const loading = ref(false);
const error = ref('');
const activeTab = ref('buyer'); // 'buyer' or 'seller'
const filters = ref({
  page: 1,
  size: 10,
  status: ''
});
const total = ref(0);
const pages = ref(0);

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'PENDING_PAYMENT', label: '待支付' },
  { value: 'PAID', label: '已支付' },
  { value: 'SHIPPED', label: '已发货' },
  { value: 'RECEIVED', label: '已收货' },
  { value: 'BREACH', label: '违约' },
  { value: 'CANCELLED', label: '已取消' }
];

const statusMap = {
  'PENDING_PAYMENT': '待支付',
  'PAID': '已支付',
  'SHIPPED': '已发货',
  'RECEIVED': '已收货',
  'BREACH': '违约',
  'CANCELLED': '已取消'
};

const loadOrders = async () => {
  loading.value = true;
  error.value = '';

  try {
    let response;
    const params = {
      page: filters.value.page,
      size: filters.value.size
    };
    if (filters.value.status) {
      params.status = filters.value.status;
    }

    if (activeTab.value === 'buyer') {
      response = await ordersAPI.myBuyerOrders(params);
    } else {
      response = await ordersAPI.mySellerOrders(params);
    }

    orders.value = response.data.records || [];
    total.value = response.data.total || 0;
    pages.value = response.data.pages || 0;
  } catch (err) {
    error.value = '加载订单失败';
    console.error('Load orders error:', err);
  } finally {
    loading.value = false;
  }
};

const switchTab = (tab) => {
  activeTab.value = tab;
  filters.value.page = 1;
  loadOrders();
};

const applyFilters = () => {
  filters.value.page = 1;
  loadOrders();
};

const nextPage = () => {
  if (filters.value.page < pages.value) {
    filters.value.page++;
    loadOrders();
  }
};

const prevPage = () => {
  if (filters.value.page > 1) {
    filters.value.page--;
    loadOrders();
  }
};

const viewOrder = (orderId) => {
  router.push(`/orders/${orderId}`);
};

const payOrder = async (orderId) => {
  if (!confirm('确定支付该订单吗？')) return;

  try {
    await ordersAPI.pay(orderId);
    alert('支付成功！');
    loadOrders();
  } catch (err) {
    alert('支付失败：' + (err.response?.data?.message || err.message));
  }
};

const shipOrder = async (orderId) => {
  if (!confirm('确定将该订单标记为已发货吗？')) return;

  try {
    await ordersAPI.ship(orderId);
    alert('操作成功！');
    loadOrders();
  } catch (err) {
    alert('操作失败：' + (err.response?.data?.message || err.message));
  }
};

const receiveOrder = async (orderId) => {
  if (!confirm('确定确认收货吗？')) return;

  try {
    await ordersAPI.receive(orderId);
    alert('确认收货成功！');
    loadOrders();
  } catch (err) {
    alert('操作失败：' + (err.response?.data?.message || err.message));
  }
};

const getStatusLabel = (status) => {
  return statusMap[status] || status;
};

onMounted(() => {
  if (!authStore.isAuthenticated) {
    router.push('/login');
    return;
  }
  loadOrders();
});
</script>

<template>
  <div class="orders-page">
    <div class="container">
      <h1 class="page-title">我的订单</h1>

      <!-- 标签页切换 -->
      <div class="tabs">
        <button
          :class="['tab-btn', { active: activeTab === 'buyer' }]"
          @click="switchTab('buyer')"
        >
          我买到的
        </button>
        <button
          :class="['tab-btn', { active: activeTab === 'seller' }]"
          @click="switchTab('seller')"
        >
          我卖出的
        </button>
      </div>

      <!-- 筛选 -->
      <div class="filters">
        <select v-model="filters.status" class="filter-select">
          <option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </option>
        </select>
        <button @click="applyFilters" class="btn btn-primary">筛选</button>
      </div>

      <!-- 加载状态 -->
      <div v-if="loading" class="loading">加载中...</div>

      <!-- 错误状态 -->
      <div v-if="error" class="error">{{ error }}</div>

      <!-- 订单列表 -->
      <div v-if="!loading && !error && orders.length > 0" class="orders-table">
        <table>
          <thead>
            <tr>
              <th>订单ID</th>
              <th>拍品ID</th>
              <th>成交价</th>
              <th>状态</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="order in orders" :key="order.id">
              <td>{{ order.id }}</td>
              <td>{{ order.itemId }}</td>
              <td>¥{{ order.finalPrice }}</td>
              <td>
                <span :class="['status-badge', `status-${order.status?.toLowerCase()}`]">
                  {{ getStatusLabel(order.status) }}
                </span>
              </td>
              <td>{{ order.createdAt ? new Date(order.createdAt).toLocaleString() : '-' }}</td>
              <td class="actions-cell">
                <button @click="viewOrder(order.id)" class="btn-small btn-view">
                  详情
                </button>
                <!-- 买家操作 -->
                <template v-if="activeTab === 'buyer'">
                  <button
                    v-if="order.status === 'PENDING_PAYMENT'"
                    @click="payOrder(order.id)"
                    class="btn-small btn-pay"
                  >
                    支付
                  </button>
                  <button
                    v-if="order.status === 'SHIPPED'"
                    @click="receiveOrder(order.id)"
                    class="btn-small btn-receive"
                  >
                    确认收货
                  </button>
                </template>
                <!-- 卖家操作 -->
                <template v-if="activeTab === 'seller'">
                  <button
                    v-if="order.status === 'PAID'"
                    @click="shipOrder(order.id)"
                    class="btn-small btn-ship"
                  >
                    发货
                  </button>
                </template>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 空状态 -->
      <div v-if="!loading && !error && orders.length === 0" class="empty-state">
        <p>暂无订单</p>
      </div>

      <!-- 分页 -->
      <div v-if="!loading && orders.length > 0" class="pagination">
        <button
          @click="prevPage"
          :disabled="filters.page <= 1"
          class="btn btn-secondary"
        >
          上一页
        </button>
        <span class="page-info">
          第 {{ filters.page }} / {{ pages }} 页 (共 {{ total }} 项)
        </span>
        <button
          @click="nextPage"
          :disabled="filters.page >= pages"
          class="btn btn-secondary"
        >
          下一页
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.orders-page {
  min-height: 400px;
}

.page-title {
  text-align: center;
  margin-bottom: 2rem;
  color: #2c3e50;
}

.tabs {
  display: flex;
  gap: 1rem;
  margin-bottom: 1.5rem;
  border-bottom: 2px solid #eee;
  padding-bottom: 0.5rem;
}

.tab-btn {
  padding: 0.75rem 1.5rem;
  border: none;
  background: none;
  font-size: 1rem;
  cursor: pointer;
  color: #666;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
}

.tab-btn.active {
  color: #3498db;
  border-bottom-color: #3498db;
}

.tab-btn:hover {
  color: #3498db;
}

.filters {
  display: flex;
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.filter-select {
  padding: 0.5rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 1rem;
}

.btn {
  padding: 0.5rem 1rem;
  border: none;
  border-radius: 4px;
  font-size: 1rem;
  cursor: pointer;
}

.btn-primary {
  background-color: #3498db;
  color: white;
}

.btn-primary:hover {
  background-color: #2980b9;
}

.btn-secondary {
  background-color: #95a5a6;
  color: white;
}

.btn-secondary:hover:not(:disabled) {
  background-color: #7f8c8d;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.loading,
.error,
.empty-state {
  text-align: center;
  padding: 3rem;
  font-size: 1.2rem;
}

.error {
  color: #e74c3c;
}

.orders-table {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  padding: 1rem;
  text-align: left;
  border-bottom: 1px solid #eee;
}

th {
  background-color: #f8f9fa;
  font-weight: 600;
  color: #555;
}

.status-badge {
  display: inline-block;
  padding: 0.25rem 0.75rem;
  border-radius: 12px;
  font-size: 0.875rem;
  font-weight: 500;
}

.status-pending_payment {
  background-color: #f39c12;
  color: white;
}

.status-paid {
  background-color: #27ae60;
  color: white;
}

.status-shipped {
  background-color: #3498db;
  color: white;
}

.status-received {
  background-color: #2ecc71;
  color: white;
}

.status-breach {
  background-color: #e74c3c;
  color: white;
}

.status-cancelled {
  background-color: #95a5a6;
  color: white;
}

.actions-cell {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.btn-small {
  padding: 0.4rem 0.8rem;
  border: none;
  border-radius: 4px;
  font-size: 0.875rem;
  cursor: pointer;
}

.btn-view {
  background-color: #3498db;
  color: white;
}

.btn-pay {
  background-color: #27ae60;
  color: white;
}

.btn-ship {
  background-color: #9b59b6;
  color: white;
}

.btn-receive {
  background-color: #2ecc71;
  color: white;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 1rem;
  margin-top: 2rem;
}

.page-info {
  color: #666;
}
</style>
