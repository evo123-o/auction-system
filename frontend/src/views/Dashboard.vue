<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { itemsAPI } from '../api/items';

const router = useRouter();

const myItems = ref([]);
const loading = ref(false);

const loadMyItems = async () => {
  loading.value = true;
  try {
    // Load all items and filter by current user (simplified)
    const response = await itemsAPI.list({ page: 1, size: 100 });
    myItems.value = response.data.records || [];
  } catch (err) {
    console.error('Load items error:', err);
  } finally {
    loading.value = false;
  }
};

const viewItem = (itemId) => {
  router.push(`/items/${itemId}`);
};

const editItem = (itemId) => {
  router.push(`/items/${itemId}/edit`);
};

const deleteItem = async (itemId) => {
  if (!confirm('确定要删除这个拍品吗？')) {
    return;
  }

  try {
    await itemsAPI.delete(itemId);
    await loadMyItems();
    alert('删除成功');
  } catch (err) {
    alert('删除失败：' + (err.response?.data?.message || err.response?.data));
  }
};

onMounted(() => {
  loadMyItems();
});
</script>

<template>
  <div class="dashboard-page">
    <div class="container">
      <h1 class="page-title">我的拍品</h1>

      <div class="actions">
        <router-link to="/items/create" class="btn btn-primary">
          + 创建新拍品
        </router-link>
      </div>

      <div v-if="loading" class="loading">加载中...</div>

      <div v-if="!loading && myItems.length === 0" class="empty-state">
        <p>您还没有创建任何拍品</p>
        <router-link to="/items/create" class="btn btn-primary">
          创建第一个拍品
        </router-link>
      </div>

      <div v-if="!loading && myItems.length > 0" class="items-table">
        <table>
          <thead>
            <tr>
              <th>标题</th>
              <th>起拍价</th>
              <th>当前价</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in myItems" :key="item.id">
              <td>{{ item.title }}</td>
              <td>¥{{ item.startingPrice }}</td>
              <td>¥{{ item.currentPrice || item.startingPrice }}</td>
              <td>
                <span :class="['status-badge', `status-${item.status?.toLowerCase()}`]">
                  {{ item.status }}
                </span>
              </td>
              <td class="actions-cell">
                <button @click="viewItem(item.id)" class="btn-small btn-view">
                  查看
                </button>
                <button @click="editItem(item.id)" class="btn-small btn-edit">
                  编辑
                </button>
                <button @click="deleteItem(item.id)" class="btn-small btn-delete">
                  删除
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dashboard-page {
  min-height: 400px;
}

.page-title {
  text-align: center;
  margin-bottom: 2rem;
  color: #2c3e50;
}

.actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 2rem;
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
}

.btn-primary {
  background-color: #3498db;
  color: white;
}

.btn-primary:hover {
  background-color: #2980b9;
}

.loading,
.empty-state {
  text-align: center;
  padding: 3rem;
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

.items-table {
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

table {
  width: 100%;
  border-collapse: collapse;
}

thead {
  background-color: #f8f9fa;
}

th,
td {
  padding: 1rem;
  text-align: left;
  border-bottom: 1px solid #eee;
}

th {
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

.actions-cell {
  display: flex;
  gap: 0.5rem;
}

.btn-small {
  padding: 0.4rem 0.8rem;
  border: none;
  border-radius: 4px;
  font-size: 0.875rem;
  cursor: pointer;
  transition: background-color 0.3s;
}

.btn-view {
  background-color: #3498db;
  color: white;
}

.btn-view:hover {
  background-color: #2980b9;
}

.btn-edit {
  background-color: #f39c12;
  color: white;
}

.btn-edit:hover {
  background-color: #e67e22;
}

.btn-delete {
  background-color: #e74c3c;
  color: white;
}

.btn-delete:hover {
  background-color: #c0392b;
}
</style>
