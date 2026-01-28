<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import { depositsAPI } from '../api/deposits';

const router = useRouter();
const authStore = useAuthStore();

const deposits = ref([]);
const loading = ref(false);
const error = ref('');

const statusMap = {
  'PENDING': '待支付',
  'PAID': '已支付',
  'FROZEN': '已冻结',
  'REFUNDED': '已退还',
  'FORFEITED': '已罚没'
};

const loadDeposits = async () => {
  loading.value = true;
  error.value = '';

  try {
    const response = await depositsAPI.myDeposits();
    deposits.value = response.data.data || [];
  } catch (err) {
    error.value = '加载保证金记录失败';
    console.error('Load deposits error:', err);
  } finally {
    loading.value = false;
  }
};

const payDeposit = async (depositId) => {
  if (!confirm('确定支付该保证金吗？')) return;

  try {
    await depositsAPI.pay(depositId);
    alert('支付成功！');
    loadDeposits();
  } catch (err) {
    alert('支付失败：' + (err.response?.data?.message || err.message));
  }
};

const viewItem = (itemId) => {
  router.push(`/items/${itemId}`);
};

const getStatusLabel = (status) => {
  return statusMap[status] || status;
};

onMounted(() => {
  if (!authStore.isAuthenticated) {
    router.push('/login');
    return;
  }
  loadDeposits();
});
</script>

<template>
  <div class="deposits-page">
    <div class="container">
      <h1 class="page-title">我的保证金</h1>

      <div v-if="loading" class="loading">加载中...</div>
      <div v-if="error" class="error">{{ error }}</div>

      <div v-if="!loading && !error && deposits.length > 0" class="deposits-table">
        <table>
          <thead>
            <tr>
              <th>保证金ID</th>
              <th>拍品ID</th>
              <th>金额</th>
              <th>状态</th>
              <th>支付单号</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="deposit in deposits" :key="deposit.id">
              <td>{{ deposit.id }}</td>
              <td>
                <a @click.prevent="viewItem(deposit.itemId)" href="#" class="link">
                  {{ deposit.itemId }}
                </a>
              </td>
              <td>¥{{ deposit.amount }}</td>
              <td>
                <span :class="['status-badge', `status-${deposit.status?.toLowerCase()}`]">
                  {{ getStatusLabel(deposit.status) }}
                </span>
              </td>
              <td>{{ deposit.paymentRef || '-' }}</td>
              <td>{{ deposit.createdAt ? new Date(deposit.createdAt).toLocaleString() : '-' }}</td>
              <td>
                <button
                  v-if="deposit.status === 'PENDING'"
                  @click="payDeposit(deposit.id)"
                  class="btn-small btn-pay"
                >
                  支付
                </button>
                <button
                  @click="viewItem(deposit.itemId)"
                  class="btn-small btn-view"
                >
                  查看拍品
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="!loading && !error && deposits.length === 0" class="empty-state">
        <p>暂无保证金记录</p>
        <router-link to="/" class="btn btn-primary">去浏览拍品</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.deposits-page {
  min-height: 400px;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 2rem;
}

.page-title {
  text-align: center;
  margin-bottom: 2rem;
  color: #2c3e50;
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

.empty-state {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.empty-state p {
  margin-bottom: 1rem;
  color: #777;
}

.deposits-table {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow-x: auto;
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
  white-space: nowrap;
}

.link {
  color: #3498db;
  text-decoration: none;
}

.link:hover {
  text-decoration: underline;
}

.status-badge {
  display: inline-block;
  padding: 0.25rem 0.75rem;
  border-radius: 12px;
  font-size: 0.875rem;
  font-weight: 500;
}

.status-pending {
  background-color: #f39c12;
  color: white;
}

.status-paid {
  background-color: #27ae60;
  color: white;
}

.status-frozen {
  background-color: #3498db;
  color: white;
}

.status-refunded {
  background-color: #2ecc71;
  color: white;
}

.status-forfeited {
  background-color: #e74c3c;
  color: white;
}

.btn {
  padding: 0.75rem 1.5rem;
  border: none;
  border-radius: 4px;
  font-size: 1rem;
  cursor: pointer;
  text-decoration: none;
  display: inline-block;
}

.btn-primary {
  background-color: #3498db;
  color: white;
}

.btn-small {
  padding: 0.4rem 0.8rem;
  border: none;
  border-radius: 4px;
  font-size: 0.875rem;
  cursor: pointer;
  margin-right: 0.5rem;
}

.btn-pay {
  background-color: #27ae60;
  color: white;
}

.btn-view {
  background-color: #3498db;
  color: white;
}
</style>
