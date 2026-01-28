<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import { itemsAPI } from '../api/items';
import { bidsAPI } from '../api/bids';
import { depositsAPI } from '../api/deposits';

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

// 保证金状态
const depositStatus = ref({
  eligible: false,
  depositId: null,
  status: null
});
const depositLoading = ref(false);

// 倒计时
const countdown = ref({
  days: 0,
  hours: 0,
  minutes: 0,
  seconds: 0
});
let countdownTimer = null;

// 自动刷新
let autoRefreshTimer = null;
const AUTO_REFRESH_INTERVAL = 3000; // 3秒

const itemId = computed(() => parseInt(route.params.id));

const loadItem = async () => {
  loading.value = true;
  error.value = '';
  
  try {
    const response = await itemsAPI.getById(itemId.value);
    item.value = response.data;
    startCountdown();
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

const checkDepositStatus = async () => {
  if (!authStore.isAuthenticated) return;
  
  depositLoading.value = true;
  try {
    const response = await depositsAPI.checkStatus(itemId.value);
    depositStatus.value.eligible = response.data.data?.eligible || false;
  } catch (err) {
    console.error('Check deposit status error:', err);
  } finally {
    depositLoading.value = false;
  }
};

const initAndPayDeposit = async () => {
  if (!authStore.isAuthenticated) {
    router.push({ name: 'Login', query: { redirect: route.fullPath } });
    return;
  }

  depositLoading.value = true;
  try {
    // 初始化保证金记录
    const initResponse = await depositsAPI.init(itemId.value);
    const depositId = initResponse.data.data?.depositId;
    
    if (!depositId) {
      alert('初始化保证金失败');
      return;
    }

    // 模拟支付
    await depositsAPI.pay(depositId);
    alert('保证金支付成功！现在可以出价了');
    
    // 刷新保证金状态
    await checkDepositStatus();
  } catch (err) {
    alert('保证金支付失败：' + (err.response?.data?.message || err.message));
  } finally {
    depositLoading.value = false;
  }
};

const placeBid = async () => {
  if (!authStore.isAuthenticated) {
    router.push({ name: 'Login', query: { redirect: route.fullPath } });
    return;
  }

  // 检查保证金
  if (!depositStatus.value.eligible) {
    bidError.value = '请先缴纳保证金';
    return;
  }

  bidError.value = '';
  const amount = parseFloat(bidAmount.value);
  
  if (!amount || amount <= 0) {
    bidError.value = '请输入有效的出价金额';
    return;
  }

  const minBid = item.value.currentPrice || item.value.startPrice;
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

// 倒计时逻辑
const startCountdown = () => {
  if (countdownTimer) {
    clearInterval(countdownTimer);
  }

  countdownTimer = setInterval(() => {
    if (!item.value || !item.value.endTime) {
      countdown.value = { days: 0, hours: 0, minutes: 0, seconds: 0 };
      return;
    }

    const endTime = new Date(item.value.endTime).getTime();
    const now = Date.now();
    const diff = endTime - now;

    if (diff <= 0) {
      countdown.value = { days: 0, hours: 0, minutes: 0, seconds: 0 };
      clearInterval(countdownTimer);
      return;
    }

    countdown.value = {
      days: Math.floor(diff / (1000 * 60 * 60 * 24)),
      hours: Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)),
      minutes: Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60)),
      seconds: Math.floor((diff % (1000 * 60)) / 1000)
    };
  }, 1000);
};

// 自动刷新（轮询）
const startAutoRefresh = () => {
  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer);
  }

  autoRefreshTimer = setInterval(async () => {
    if (item.value?.status === 'RUNNING') {
      try {
        const response = await itemsAPI.getById(itemId.value);
        const newItem = response.data;
        
        // 检查是否有变化
        if (newItem.currentPrice !== item.value.currentPrice ||
            newItem.endTime !== item.value.endTime ||
            newItem.extendCount !== item.value.extendCount) {
          item.value = newItem;
          startCountdown();
          loadBids();
        }
      } catch (err) {
        console.error('Auto refresh error:', err);
      }
    }
  }, AUTO_REFRESH_INTERVAL);
};

const isAuctionActive = computed(() => {
  return item.value?.status === 'RUNNING';
});

const formattedCountdown = computed(() => {
  const { days, hours, minutes, seconds } = countdown.value;
  if (days > 0) {
    return `${days}天 ${hours}时 ${minutes}分 ${seconds}秒`;
  }
  return `${hours}时 ${minutes}分 ${seconds}秒`;
});

const extensionInfo = computed(() => {
  if (!item.value) return null;
  const extendCount = item.value.extendCount || 0;
  const maxExtend = item.value.maxExtend || 3;
  const remaining = maxExtend - extendCount;
  return {
    current: extendCount,
    max: maxExtend,
    remaining: remaining
  };
});

onMounted(() => {
  loadItem();
  loadBids();
  checkDepositStatus();
  startAutoRefresh();
});

onUnmounted(() => {
  if (countdownTimer) {
    clearInterval(countdownTimer);
  }
  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer);
  }
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

            <!-- 倒计时区域 -->
            <div v-if="isAuctionActive && item.endTime" class="countdown-section">
              <h3>距离结束</h3>
              <div class="countdown-display">
                <div class="countdown-item">
                  <span class="countdown-value">{{ countdown.days }}</span>
                  <span class="countdown-label">天</span>
                </div>
                <div class="countdown-item">
                  <span class="countdown-value">{{ countdown.hours }}</span>
                  <span class="countdown-label">时</span>
                </div>
                <div class="countdown-item">
                  <span class="countdown-value">{{ countdown.minutes }}</span>
                  <span class="countdown-label">分</span>
                </div>
                <div class="countdown-item">
                  <span class="countdown-value">{{ countdown.seconds }}</span>
                  <span class="countdown-label">秒</span>
                </div>
              </div>
              <!-- 延时信息 -->
              <div v-if="extensionInfo" class="extension-info">
                <span v-if="extensionInfo.current > 0" class="extension-used">
                  已延时 {{ extensionInfo.current }} 次
                </span>
                <span class="extension-remaining">
                  剩余可延时 {{ extensionInfo.remaining }} 次
                </span>
              </div>
            </div>

            <div class="price-section">
              <div class="price-item">
                <span class="price-label">起拍价</span>
                <span class="price-value">¥{{ item.startPrice }}</span>
              </div>
              <div class="price-item">
                <span class="price-label">当前价</span>
                <span class="price-value current">¥{{ item.currentPrice || item.startPrice }}</span>
              </div>
              <div v-if="item.depositAmount" class="price-item">
                <span class="price-label">保证金</span>
                <span class="price-value deposit">¥{{ item.depositAmount }}</span>
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

            <!-- 保证金缴纳区域 -->
            <div v-if="authStore.isAuthenticated && isAuctionActive && !depositStatus.eligible" class="deposit-section">
              <h3>缴纳保证金</h3>
              <p class="deposit-hint">参与竞拍前需缴纳保证金 ¥{{ item.depositAmount || 0 }}</p>
              <button
                @click="initAndPayDeposit"
                class="btn btn-deposit"
                :disabled="depositLoading"
              >
                {{ depositLoading ? '处理中...' : '缴纳保证金' }}
              </button>
            </div>

            <!-- 出价区域 -->
            <div v-if="authStore.isAuthenticated && isAuctionActive" class="bid-section">
              <h3>出价</h3>
              <div v-if="depositStatus.eligible" class="deposit-status eligible">
                ✓ 保证金已缴纳，可以出价
              </div>
              <div v-else class="deposit-status not-eligible">
                ✗ 请先缴纳保证金
              </div>
              <div class="bid-form">
                <input
                  v-model="bidAmount"
                  type="number"
                  step="0.01"
                  class="bid-input"
                  placeholder="输入您的出价"
                  :disabled="bidding || !depositStatus.eligible"
                />
                <button
                  @click="placeBid"
                  class="btn btn-primary"
                  :disabled="bidding || !depositStatus.eligible"
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

        <!-- 出价历史 -->
        <div v-if="authStore.isAuthenticated && bids.length > 0" class="bid-history">
          <h3>出价历史</h3>
          <div class="bid-list">
            <div v-for="bid in bids" :key="bid.id" class="bid-item">
              <span class="bid-amount">¥{{ bid.amount }}</span>
              <span class="bid-time">{{ bid.bid_time ? new Date(bid.bid_time).toLocaleString() : '-' }}</span>
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

/* 倒计时样式 */
.countdown-section {
  padding: 1.5rem;
  background: linear-gradient(135deg, #e74c3c 0%, #c0392b 100%);
  border-radius: 8px;
  color: white;
  text-align: center;
}

.countdown-section h3 {
  color: white;
  margin-bottom: 1rem;
}

.countdown-display {
  display: flex;
  justify-content: center;
  gap: 1rem;
}

.countdown-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  background: rgba(255, 255, 255, 0.2);
  padding: 0.5rem 1rem;
  border-radius: 4px;
  min-width: 60px;
}

.countdown-value {
  font-size: 2rem;
  font-weight: bold;
}

.countdown-label {
  font-size: 0.75rem;
}

.extension-info {
  margin-top: 1rem;
  font-size: 0.875rem;
  display: flex;
  justify-content: center;
  gap: 1rem;
}

.extension-used {
  color: #f1c40f;
}

.extension-remaining {
  opacity: 0.8;
}

/* 保证金样式 */
.price-value.deposit {
  color: #9b59b6;
}

.deposit-section {
  padding: 1.5rem;
  background-color: #fff3cd;
  border: 1px solid #ffc107;
  border-radius: 8px;
}

.deposit-section h3 {
  color: #856404;
  margin-bottom: 0.5rem;
}

.deposit-hint {
  color: #856404;
  margin-bottom: 1rem;
}

.btn-deposit {
  background-color: #9b59b6;
  color: white;
}

.btn-deposit:hover:not(:disabled) {
  background-color: #8e44ad;
}

.deposit-status {
  padding: 0.5rem 1rem;
  border-radius: 4px;
  margin-bottom: 1rem;
  font-size: 0.875rem;
}

.deposit-status.eligible {
  background-color: #d4edda;
  color: #155724;
}

.deposit-status.not-eligible {
  background-color: #f8d7da;
  color: #721c24;
}

/* RUNNING 状态样式 */
.status-running {
  background-color: #e74c3c;
  color: white;
}

.status-sold {
  background-color: #27ae60;
  color: white;
}
</style>
