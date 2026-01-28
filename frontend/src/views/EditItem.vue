<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { itemsAPI } from '../api/items';

const route = useRoute();
const router = useRouter();

const form = ref({
  title: '',
  description: '',
  category: '',
  startingPrice: '',
  reservePrice: '',
  startTime: '',
  endTime: ''
});

const imageFile = ref(null);
const loading = ref(false);
const error = ref('');
const itemId = computed(() => parseInt(route.params.id));

const loadItem = async () => {
  try {
    const response = await itemsAPI.getById(itemId.value);
    const item = response.data;
    
    form.value = {
      title: item.title,
      description: item.description,
      category: item.category || '',
      startingPrice: item.startingPrice,
      reservePrice: item.reservePrice || '',
      startTime: item.startTime || '',
      endTime: item.endTime || ''
    };
  } catch (err) {
    error.value = '加载拍品失败';
    console.error('Load item error:', err);
  }
};

const handleFileChange = (event) => {
  const file = event.target.files[0];
  if (file) {
    imageFile.value = file;
  }
};

const handleSubmit = async () => {
  error.value = '';
  
  if (!form.value.title || !form.value.description || !form.value.startingPrice) {
    error.value = '请填写必填项';
    return;
  }

  loading.value = true;

  try {
    const updateData = {
      title: form.value.title,
      description: form.value.description,
      category: form.value.category || null,
      startingPrice: parseFloat(form.value.startingPrice),
      reservePrice: form.value.reservePrice ? parseFloat(form.value.reservePrice) : null,
      startTime: form.value.startTime || null,
      endTime: form.value.endTime || null
    };

    await itemsAPI.update(itemId.value, updateData);

    if (imageFile.value) {
      await itemsAPI.uploadImage(itemId.value, imageFile.value);
    }

    alert('拍品更新成功！');
    router.push(`/items/${itemId.value}`);
  } catch (err) {
    error.value = err.response?.data?.message || err.response?.data || '更新失败';
    console.error('Update item error:', err);
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  loadItem();
});
</script>

<template>
  <div class="edit-item-page">
    <div class="container">
      <h1 class="page-title">编辑拍品</h1>

      <form @submit.prevent="handleSubmit" class="item-form">
        <div class="form-group">
          <label for="title">标题 *</label>
          <input
            id="title"
            v-model="form.title"
            type="text"
            class="form-input"
            :disabled="loading"
            required
          />
        </div>

        <div class="form-group">
          <label for="description">描述 *</label>
          <textarea
            id="description"
            v-model="form.description"
            class="form-textarea"
            rows="5"
            :disabled="loading"
            required
          ></textarea>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label for="category">分类</label>
            <input
              id="category"
              v-model="form.category"
              type="text"
              class="form-input"
              :disabled="loading"
            />
          </div>

          <div class="form-group">
            <label for="startingPrice">起拍价 *</label>
            <input
              id="startingPrice"
              v-model="form.startingPrice"
              type="number"
              step="0.01"
              class="form-input"
              :disabled="loading"
              required
            />
          </div>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label for="reservePrice">保留价</label>
            <input
              id="reservePrice"
              v-model="form.reservePrice"
              type="number"
              step="0.01"
              class="form-input"
              :disabled="loading"
            />
          </div>

          <div class="form-group">
            <label for="image">更新图片</label>
            <input
              id="image"
              type="file"
              accept="image/*"
              class="form-input"
              @change="handleFileChange"
              :disabled="loading"
            />
          </div>
        </div>

        <div v-if="error" class="error-message">
          {{ error }}
        </div>

        <div class="form-actions">
          <button
            type="button"
            @click="router.back()"
            class="btn btn-secondary"
            :disabled="loading"
          >
            取消
          </button>
          <button
            type="submit"
            class="btn btn-primary"
            :disabled="loading"
          >
            {{ loading ? '更新中...' : '更新拍品' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.edit-item-page {
  min-height: 400px;
}

.page-title {
  text-align: center;
  margin-bottom: 2rem;
  color: #2c3e50;
}

.item-form {
  max-width: 800px;
  margin: 0 auto;
  background: white;
  padding: 2rem;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.form-group {
  margin-bottom: 1.5rem;
  flex: 1;
}

.form-group label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 500;
  color: #555;
}

.form-input,
.form-textarea {
  width: 100%;
  padding: 0.75rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 1rem;
  font-family: inherit;
  transition: border-color 0.3s;
}

.form-input:focus,
.form-textarea:focus {
  outline: none;
  border-color: #3498db;
}

.form-input:disabled,
.form-textarea:disabled {
  background-color: #f5f5f5;
  cursor: not-allowed;
}

.form-textarea {
  resize: vertical;
}

.form-row {
  display: flex;
  gap: 1.5rem;
}

@media (max-width: 768px) {
  .form-row {
    flex-direction: column;
  }
}

.error-message {
  padding: 0.75rem;
  background-color: #fee;
  border: 1px solid #fcc;
  border-radius: 4px;
  color: #c33;
  margin-bottom: 1.5rem;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 1rem;
  margin-top: 2rem;
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

.btn-primary:hover:not(:disabled) {
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
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
