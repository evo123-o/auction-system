<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import { itemsAPI } from '../api/items';
import { bidsAPI } from '../api/bids';

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();

const item = ref(null);
const bids = ref([]);
const loading = ref(false);
const error = ref('');
const bidAmount = ref('');
const bidding = ref(false);
const bidError = ref('');

const itemId = computed(() => parseInt(route.params.id));

const loadItem = async () => {
  loading.value = true;
  error.value = '';
  
  try {
    const response = await itemsAPI.getById(itemId.value);
    item.value = response.data;
  } catch (err) {
    error.value = '加载拍品详情失败';
    console.error('Load item error:', err);
  } finally {
    loading.value = false;
  }
};

const loadBids = async () => {
  if (!authStore.isAuthenticated) return;
  
  try {
    const response = await bidsAPI.history(itemId.value);
    bids.value = response.data.data || [];
  } catch (err) {
    console.error('Load bids error:', err);
  }
};

const placeBid = async () => {
  if (!authStore.isAuthenticated) {
    router.push({ name: 'Login', query: { redirect: route.fullPath } });
    return;
  }

  bidError.value = '';
  const amount = parseFloat(bidAmount.value);
  
  if (!amount || amount <= 0) {
    bidError.value = '请输入有效的出价金额';
    return;
  }

  const minBid = item.value.currentPrice || item.value.startingPrice;
  if (amount <= minBid) {
    bidError.value = `出价必须高于当前价格 ¥${minBid}`;
    return;
  }

  bidding.value = true;

  try {
    await bidsAPI.placeBidOnItem(itemId.value, amount);
    bidAmount.value = '';
    await loadItem();
    await loadBids();
    alert('出价成功！');
  } catch (err) {
    bidError.value = err.response?.data?.message || '出价失败';
  } finally {
    bidding.value = false;
  }
};

onMounted(() => {
  loadItem();
  loadBids();
});
</script>

<template>
  <div class="item-detail-page">
    <div class="container">
      <button @click="router.back()" class="btn btn-back">← 返回</button>

      <div v-if="loading" class="loading">加载中...</div>
      <div v-if="error" class="error">{{ error }}</div>

      <div v-if="item" class="item-detail">
        <div class="item-main">
          <div class="item-image-large">
            <img v-if="item.imageUrl" :src="item.imageUrl" :alt="item.title" />
            <div v-else class="no-image">暂无图片</div>
          </div>

          <div class="item-info">
            <h1 class="item-title">{{ item.title }}</h1>
            
            <div class="status-row">
              <span :class="['status-badge', `status-${item.status?.toLowerCase()}`]">
                {{ item.status }}
              </span>
            </div>

            <div class="price-section">
              <div class="price-item">
                <span class="price-label">起拍价</span>
                <span class="price-value">¥{{ item.startingPrice }}</span>
              </div>
              <div class="price-item">
                <span class="price-label">当前价</span>
                <span class="price-value current">¥{{ item.currentPrice || item.startingPrice }}</span>
              </div>
            </div>

            <div class="description-section">
              <h3>拍品描述</h3>
              <p>{{ item.description }}</p>
            </div>

            <div v-if="item.category" class="meta-info">
              <span class="meta-label">分类：</span>
              <span class="meta-value">{{ item.category }}</span>
            </div>

            <!-- Bidding Section -->
            <div v-if="authStore.isAuthenticated && item.status === 'ACTIVE'" class="bid-section">
              <h3>出价</h3>
              <div class="bid-form">
                <input
                  v-model="bidAmount"
                  type="number"
                  step="0.01"
                  class="bid-input"
                  placeholder="输入您的出价"
                  :disabled="bidding"
                />
                <button
                  @click="placeBid"
                  class="btn btn-primary"
                  :disabled="bidding"
                >
                  {{ bidding ? '出价中...' : '出价' }}
                </button>
              </div>
              <div v-if="bidError" class="bid-error">{{ bidError }}</div>
            </div>

            <div v-else-if="!authStore.isAuthenticated" class="login-prompt">
              <router-link to="/login" class="btn btn-primary">登录后出价</router-link>
            </div>
          </div>
        </div>

        <!-- Bid History -->
        <div v-if="authStore.isAuthenticated && bids.length > 0" class="bid-history">
          <h3>出价历史</h3>
          <div class="bid-list">
            <div v-for="bid in bids" :key="bid.id" class="bid-item">
              <span class="bid-amount">¥{{ bid.amount }}</span>
              <span class="bid-time">{{ new Date(bid.createdAt).toLocaleString() }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.item-detail-page {
  min-height: 400px;
}

.btn-back {
  margin-bottom: 1rem;
  padding: 0.5rem 1rem;
  background-color: #95a5a6;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.btn-back:hover {
  background-color: #7f8c8d;
}

.loading,
.error {
  text-align: center;
  padding: 3rem;
  font-size: 1.2rem;
}

.error {
  color: #e74c3c;
}

.item-detail {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.item-main {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 2rem;
  padding: 2rem;
}

@media (max-width: 768px) {
  .item-main {
    grid-template-columns: 1fr;
  }
}

.item-image-large {
  width: 100%;
  height: 400px;
  background-color: #f5f5f5;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  overflow: hidden;
}

.item-image-large img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.no-image {
  color: #999;
  font-size: 1.2rem;
}

.item-info {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.item-title {
  font-size: 2rem;
  color: #2c3e50;
  margin: 0;
}

.status-row {
  display: flex;
  align-items: center;
}

.status-badge {
  display: inline-block;
  padding: 0.5rem 1rem;
  border-radius: 16px;
  font-size: 0.875rem;
  font-weight: 500;
}

.status-pending {
  background-color: #f39c12;
  color: white;
}

.status-approved {
  background-color: #3498db;
  color: white;
}

.status-active {
  background-color: #27ae60;
  color: white;
}

.status-closed {
  background-color: #95a5a6;
  color: white;
}

.price-section {
  display: flex;
  gap: 2rem;
  padding: 1rem;
  background-color: #f8f9fa;
  border-radius: 8px;
}

.price-item {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.price-label {
  color: #777;
  font-size: 0.875rem;
}

.price-value {
  font-size: 1.5rem;
  font-weight: bold;
  color: #2c3e50;
}

.price-value.current {
  color: #27ae60;
}

.description-section h3,
.bid-section h3,
.bid-history h3 {
  margin-bottom: 0.5rem;
  color: #2c3e50;
}

.description-section p {
  color: #555;
  line-height: 1.6;
}

.meta-info {
  padding: 0.75rem;
  background-color: #f8f9fa;
  border-radius: 4px;
}

.meta-label {
  font-weight: 500;
  color: #777;
}

.meta-value {
  color: #2c3e50;
}

.bid-section {
  padding: 1.5rem;
  background-color: #f0f8ff;
  border-radius: 8px;
}

.bid-form {
  display: flex;
  gap: 1rem;
  margin-top: 1rem;
}

.bid-input {
  flex: 1;
  padding: 0.75rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 1rem;
}

.btn {
  padding: 0.75rem 1.5rem;
  border: none;
  border-radius: 4px;
  font-size: 1rem;
  cursor: pointer;
  transition: background-color 0.3s;
  text-decoration: none;
  display: inline-block;
  text-align: center;
}

.btn-primary {
  background-color: #3498db;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background-color: #2980b9;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.bid-error {
  margin-top: 0.5rem;
  padding: 0.5rem;
  background-color: #fee;
  border: 1px solid #fcc;
  border-radius: 4px;
  color: #c33;
}

.login-prompt {
  padding: 1.5rem;
  text-align: center;
  background-color: #f8f9fa;
  border-radius: 8px;
}

.bid-history {
  padding: 2rem;
  border-top: 1px solid #eee;
}

.bid-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin-top: 1rem;
}

.bid-item {
  display: flex;
  justify-content: space-between;
  padding: 1rem;
  background-color: #f8f9fa;
  border-radius: 4px;
}

.bid-amount {
  font-weight: bold;
  color: #27ae60;
}

.bid-time {
  color: #777;
  font-size: 0.875rem;
}
</style>
