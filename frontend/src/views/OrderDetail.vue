<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import { ordersAPI } from '../api/orders';
import { logisticsAPI } from '../api/logistics';
import { evaluationsAPI } from '../api/evaluations';

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();

const order = ref(null);
const logistics = ref(null);
const evaluations = ref([]);
const hasReviewed = ref(false);
const loading = ref(false);
const error = ref('');

// 物流表单
const logisticsForm = ref({
  company: '',
  trackingNo: '',
  notes: ''
});
const showLogisticsForm = ref(false);

// 评价表单
const evaluationForm = ref({
  rating: 5,
  comment: ''
});
const showEvaluationForm = ref(false);

const orderId = computed(() => parseInt(route.params.id));

const statusMap = {
  'PENDING_PAYMENT': '待支付',
  'PAID': '已支付',
  'SHIPPED': '已发货',
  'RECEIVED': '已收货',
  'BREACH': '违约',
  'CANCELLED': '已取消'
};

const loadOrder = async () => {
  loading.value = true;
  error.value = '';

  try {
    const response = await ordersAPI.getById(orderId.value);
    order.value = response.data.data;
  } catch (err) {
    error.value = '加载订单详情失败';
    console.error('Load order error:', err);
  } finally {
    loading.value = false;
  }
};

const loadLogistics = async () => {
  try {
    const response = await logisticsAPI.getByOrderId(orderId.value);
    logistics.value = response.data.data;
    if (logistics.value) {
      logisticsForm.value = {
        company: logistics.value.company || '',
        trackingNo: logistics.value.trackingNo || '',
        notes: logistics.value.notes || ''
      };
    }
  } catch (err) {
    console.error('Load logistics error:', err);
  }
};

const loadEvaluations = async () => {
  try {
    const response = await evaluationsAPI.getByOrderId(orderId.value);
    evaluations.value = response.data.data || [];
  } catch (err) {
    console.error('Load evaluations error:', err);
  }
};

const checkHasReviewed = async () => {
  try {
    const response = await evaluationsAPI.checkReviewed(orderId.value);
    hasReviewed.value = response.data.data?.hasReviewed || false;
  } catch (err) {
    console.error('Check reviewed error:', err);
  }
};

const saveLogistics = async () => {
  try {
    await logisticsAPI.saveOrUpdate(orderId.value, logisticsForm.value);
    alert('物流信息保存成功！');
    showLogisticsForm.value = false;
    loadLogistics();
  } catch (err) {
    alert('保存失败：' + (err.response?.data?.message || err.message));
  }
};

const submitEvaluation = async () => {
  try {
    await evaluationsAPI.create({
      orderId: orderId.value,
      rating: evaluationForm.value.rating,
      comment: evaluationForm.value.comment
    });
    alert('评价提交成功！');
    showEvaluationForm.value = false;
    loadEvaluations();
    checkHasReviewed();
  } catch (err) {
    alert('评价失败：' + (err.response?.data?.message || err.message));
  }
};

const payOrder = async () => {
  if (!confirm('确定支付该订单吗？')) return;

  try {
    await ordersAPI.pay(orderId.value);
    alert('支付成功！');
    loadOrder();
  } catch (err) {
    alert('支付失败：' + (err.response?.data?.message || err.message));
  }
};

const shipOrder = async () => {
  if (!confirm('确定将该订单标记为已发货吗？')) return;

  try {
    await ordersAPI.ship(orderId.value);
    alert('操作成功！');
    loadOrder();
  } catch (err) {
    alert('操作失败：' + (err.response?.data?.message || err.message));
  }
};

const receiveOrder = async () => {
  if (!confirm('确定确认收货吗？')) return;

  try {
    await ordersAPI.receive(orderId.value);
    alert('确认收货成功！');
    loadOrder();
  } catch (err) {
    alert('操作失败：' + (err.response?.data?.message || err.message));
  }
};

const getStatusLabel = (status) => {
  return statusMap[status] || status;
};

const viewReceipt = async () => {
  try {
    const response = await ordersAPI.getReceipt(orderId.value);
    const receiptPath = response.data.data?.receiptPath;
    alert('凭证已生成：' + receiptPath);
  } catch (err) {
    alert('生成凭证失败：' + (err.response?.data?.message || err.message));
  }
};

onMounted(() => {
  if (!authStore.isAuthenticated) {
    router.push('/login');
    return;
  }
  loadOrder();
  loadLogistics();
  loadEvaluations();
  checkHasReviewed();
});
</script>

<template>
  <div class="order-detail-page">
    <div class="container">
      <button @click="router.back()" class="btn btn-back">← 返回</button>

      <div v-if="loading" class="loading">加载中...</div>
      <div v-if="error" class="error">{{ error }}</div>

      <div v-if="order" class="order-detail">
        <h1 class="page-title">订单详情</h1>

        <!-- 基本信息 -->
        <div class="info-section">
          <h3>基本信息</h3>
          <div class="info-grid">
            <div class="info-item">
              <span class="label">订单ID</span>
              <span class="value">{{ order.id }}</span>
            </div>
            <div class="info-item">
              <span class="label">拍品ID</span>
              <span class="value">{{ order.itemId }}</span>
            </div>
            <div class="info-item">
              <span class="label">买家ID</span>
              <span class="value">{{ order.buyerId }}</span>
            </div>
            <div class="info-item">
              <span class="label">卖家ID</span>
              <span class="value">{{ order.sellerId }}</span>
            </div>
            <div class="info-item">
              <span class="label">成交价</span>
              <span class="value price">¥{{ order.finalPrice }}</span>
            </div>
            <div class="info-item">
              <span class="label">状态</span>
              <span :class="['status-badge', `status-${order.status?.toLowerCase()}`]">
                {{ getStatusLabel(order.status) }}
              </span>
            </div>
            <div class="info-item">
              <span class="label">创建时间</span>
              <span class="value">{{ order.createdAt ? new Date(order.createdAt).toLocaleString() : '-' }}</span>
            </div>
            <div class="info-item">
              <span class="label">支付截止</span>
              <span class="value">{{ order.payBy ? new Date(order.payBy).toLocaleString() : '-' }}</span>
            </div>
          </div>
        </div>

        <!-- 操作按钮 -->
        <div class="actions-section">
          <button
            v-if="order.status === 'PENDING_PAYMENT'"
            @click="payOrder"
            class="btn btn-pay"
          >
            支付订单
          </button>
          <button
            v-if="order.status === 'PAID'"
            @click="shipOrder"
            class="btn btn-ship"
          >
            发货
          </button>
          <button
            v-if="order.status === 'SHIPPED'"
            @click="receiveOrder"
            class="btn btn-receive"
          >
            确认收货
          </button>
          <button @click="viewReceipt" class="btn btn-receipt">
            查看凭证
          </button>
        </div>

        <!-- 物流信息 -->
        <div class="logistics-section">
          <h3>
            物流信息
            <button
              v-if="['PAID', 'SHIPPED'].includes(order.status)"
              @click="showLogisticsForm = !showLogisticsForm"
              class="btn-small btn-edit"
            >
              {{ showLogisticsForm ? '取消' : (logistics ? '编辑' : '添加') }}
            </button>
          </h3>

          <div v-if="logistics && !showLogisticsForm" class="logistics-info">
            <p><strong>物流公司：</strong>{{ logistics.company }}</p>
            <p><strong>物流单号：</strong>{{ logistics.trackingNo }}</p>
            <p v-if="logistics.notes"><strong>备注：</strong>{{ logistics.notes }}</p>
            <p><strong>更新时间：</strong>{{ logistics.updatedAt ? new Date(logistics.updatedAt).toLocaleString() : '-' }}</p>
          </div>

          <div v-else-if="!logistics && !showLogisticsForm" class="no-data">
            暂无物流信息
          </div>

          <div v-if="showLogisticsForm" class="logistics-form">
            <div class="form-group">
              <label>物流公司</label>
              <input v-model="logisticsForm.company" type="text" placeholder="请输入物流公司" />
            </div>
            <div class="form-group">
              <label>物流单号</label>
              <input v-model="logisticsForm.trackingNo" type="text" placeholder="请输入物流单号" />
            </div>
            <div class="form-group">
              <label>备注</label>
              <textarea v-model="logisticsForm.notes" placeholder="请输入备注（选填）"></textarea>
            </div>
            <button @click="saveLogistics" class="btn btn-primary">保存物流信息</button>
          </div>
        </div>

        <!-- 评价 -->
        <div class="evaluation-section">
          <h3>
            交易评价
            <button
              v-if="['PAID', 'SHIPPED', 'RECEIVED'].includes(order.status) && !hasReviewed"
              @click="showEvaluationForm = !showEvaluationForm"
              class="btn-small btn-edit"
            >
              {{ showEvaluationForm ? '取消' : '评价' }}
            </button>
          </h3>

          <div v-if="evaluations.length > 0" class="evaluations-list">
            <div v-for="evaluation in evaluations" :key="evaluation.id" class="evaluation-item">
              <div class="rating">
                <span v-for="i in 5" :key="i" :class="['star', { filled: i <= evaluation.rating }]">★</span>
              </div>
              <p class="comment">{{ evaluation.comment || '暂无评价内容' }}</p>
              <p class="meta">评价时间：{{ evaluation.createdAt ? new Date(evaluation.createdAt).toLocaleString() : '-' }}</p>
            </div>
          </div>

          <div v-else-if="!showEvaluationForm" class="no-data">
            暂无评价
          </div>

          <div v-if="showEvaluationForm" class="evaluation-form">
            <div class="form-group">
              <label>评分</label>
              <div class="rating-input">
                <span
                  v-for="i in 5"
                  :key="i"
                  :class="['star', { filled: i <= evaluationForm.rating }]"
                  @click="evaluationForm.rating = i"
                >★</span>
              </div>
            </div>
            <div class="form-group">
              <label>评价内容</label>
              <textarea v-model="evaluationForm.comment" placeholder="请输入评价内容（选填）"></textarea>
            </div>
            <button @click="submitEvaluation" class="btn btn-primary">提交评价</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.order-detail-page {
  min-height: 400px;
}

.container {
  max-width: 900px;
  margin: 0 auto;
  padding: 2rem;
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

.page-title {
  text-align: center;
  margin-bottom: 2rem;
  color: #2c3e50;
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

.order-detail {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  padding: 2rem;
}

.info-section,
.logistics-section,
.evaluation-section {
  margin-bottom: 2rem;
  padding-bottom: 1.5rem;
  border-bottom: 1px solid #eee;
}

h3 {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
  color: #2c3e50;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 1rem;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.info-item .label {
  font-size: 0.875rem;
  color: #666;
}

.info-item .value {
  font-size: 1rem;
  color: #2c3e50;
}

.info-item .value.price {
  font-weight: bold;
  color: #27ae60;
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

.actions-section {
  display: flex;
  gap: 1rem;
  margin-bottom: 2rem;
  flex-wrap: wrap;
}

.btn {
  padding: 0.75rem 1.5rem;
  border: none;
  border-radius: 4px;
  font-size: 1rem;
  cursor: pointer;
}

.btn-primary {
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

.btn-receipt {
  background-color: #34495e;
  color: white;
}

.btn-small {
  padding: 0.25rem 0.5rem;
  font-size: 0.75rem;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.btn-edit {
  background-color: #f39c12;
  color: white;
}

.no-data {
  color: #999;
  font-style: italic;
}

.logistics-info,
.evaluation-item {
  background-color: #f8f9fa;
  padding: 1rem;
  border-radius: 4px;
  margin-bottom: 1rem;
}

.logistics-info p,
.evaluation-item p {
  margin: 0.5rem 0;
}

.logistics-form,
.evaluation-form {
  background-color: #f8f9fa;
  padding: 1rem;
  border-radius: 4px;
}

.form-group {
  margin-bottom: 1rem;
}

.form-group label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 500;
}

.form-group input,
.form-group textarea {
  width: 100%;
  padding: 0.5rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 1rem;
}

.form-group textarea {
  min-height: 100px;
  resize: vertical;
}

.rating,
.rating-input {
  display: flex;
  gap: 0.25rem;
}

.star {
  font-size: 1.5rem;
  color: #ddd;
  cursor: pointer;
}

.star.filled {
  color: #f39c12;
}

.evaluations-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.evaluation-item .comment {
  color: #2c3e50;
}

.evaluation-item .meta {
  font-size: 0.875rem;
  color: #999;
}
</style>
