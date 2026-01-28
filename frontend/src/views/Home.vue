<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { itemsAPI } from '../api/items';

const router = useRouter();

const items = ref([]);
const loading = ref(false);
const error = ref('');
const filters = ref({
  page: 1,
  size: 12,
  title: '',
  category: '',
  status: ''
});
const total = ref(0);
const pages = ref(0);

const loadItems = async () => {
  loading.value = true;
  error.value = '';
  
  try {
    const response = await itemsAPI.list(filters.value);
    items.value = response.data.records || [];
    total.value = response.data.total || 0;
    pages.value = response.data.pages || 0;
  } catch (err) {
    error.value = '加载拍品列表失败';
    console.error('Load items error:', err);
  } finally {
    loading.value = false;
  }
};

const viewDetail = (itemId) => {
  router.push(`/items/${itemId}`);
};

const nextPage = () => {
  if (filters.value.page < pages.value) {
    filters.value.page++;
    loadItems();
  }
};

const prevPage = () => {
  if (filters.value.page > 1) {
    filters.value.page--;
    loadItems();
  }
};

const applyFilters = () => {
  filters.value.page = 1;
  loadItems();
};

onMounted(() => {
  loadItems();
});
</script>

<template>
  <div class="home-page">
    <div class="container">
      <h1 class="page-title">拍品列表</h1>

      <!-- Filters -->
      <div class="filters">
        <input
          v-model="filters.title"
          type="text"
          class="filter-input"
          placeholder="搜索拍品标题..."
          @keyup.enter="applyFilters"
        />
        <select v-model="filters.status" class="filter-select">
          <option value="">所有状态</option>
          <option value="PENDING">待审核</option>
          <option value="APPROVED">已批准</option>
          <option value="ACTIVE">进行中</option>
          <option value="CLOSED">已结束</option>
        </select>
        <button @click="applyFilters" class="btn btn-primary">搜索</button>
      </div>

      <!-- Loading state -->
      <div v-if="loading" class="loading">加载中...</div>

      <!-- Error state -->
      <div v-if="error" class="error">{{ error }}</div>

      <!-- Items grid -->
      <div v-if="!loading && !error" class="items-grid">
        <div
          v-for="item in items"
          :key="item.id"
          class="item-card"
          @click="viewDetail(item.id)"
        >
          <div class="item-image">
            <img
              v-if="item.imageUrl"
              :src="item.imageUrl"
              :alt="item.title"
            />
            <div v-else class="no-image">暂无图片</div>
          </div>
          <div class="item-content">
            <h3 class="item-title">{{ item.title }}</h3>
            <p class="item-description">{{ item.description }}</p>
            <div class="item-price">
              <span class="label">起拍价:</span>
              <span class="value">¥{{ item.startingPrice }}</span>
            </div>
            <div class="item-current-price">
              <span class="label">当前价:</span>
              <span class="value">¥{{ item.currentPrice || item.startingPrice }}</span>
            </div>
            <div class="item-status">
              <span :class="['status-badge', `status-${item.status?.toLowerCase()}`]">
                {{ item.status }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- Empty state -->
      <div v-if="!loading && !error && items.length === 0" class="empty-state">
        <p>暂无拍品</p>
      </div>

      <!-- Pagination -->
      <div v-if="!loading && items.length > 0" class="pagination">
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
.home-page {
  min-height: 400px;
}

.page-title {
  text-align: center;
  margin-bottom: 2rem;
  color: #2c3e50;
}

.filters {
  display: flex;
  gap: 1rem;
  margin-bottom: 2rem;
  flex-wrap: wrap;
}

.filter-input,
.filter-select {
  flex: 1;
  min-width: 200px;
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

.items-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 2rem;
  margin-bottom: 2rem;
}

.item-card {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.3s, box-shadow 0.3s;
}

.item-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.item-image {
  width: 100%;
  height: 200px;
  background-color: #f5f5f5;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.item-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.no-image {
  color: #999;
  font-size: 1rem;
}

.item-content {
  padding: 1.5rem;
}

.item-title {
  font-size: 1.25rem;
  margin-bottom: 0.5rem;
  color: #2c3e50;
}

.item-description {
  color: #666;
  margin-bottom: 1rem;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.item-price,
.item-current-price {
  display: flex;
  justify-content: space-between;
  margin-bottom: 0.5rem;
}

.label {
  color: #777;
}

.value {
  font-weight: bold;
  color: #27ae60;
}

.item-status {
  margin-top: 1rem;
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
