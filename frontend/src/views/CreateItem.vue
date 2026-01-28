<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { itemsAPI } from '../api/items';

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
    // Create the item
    const createData = {
      title: form.value.title,
      description: form.value.description,
      category: form.value.category || null,
      startingPrice: parseFloat(form.value.startingPrice),
      reservePrice: form.value.reservePrice ? parseFloat(form.value.reservePrice) : null,
      startTime: form.value.startTime || null,
      endTime: form.value.endTime || null
    };

    const response = await itemsAPI.create(createData);
    const itemId = response.data.id;

    // Upload image if selected
    if (imageFile.value && itemId) {
      await itemsAPI.uploadImage(itemId, imageFile.value);
    }

    alert('拍品创建成功！');
    router.push(`/items/${itemId}`);
  } catch (err) {
    error.value = err.response?.data?.message || err.response?.data || '创建失败';
    console.error('Create item error:', err);
  } finally {
    loading.value = false;
  }
};
</script>

<template>
  <div class="create-item-page">
    <div class="container">
      <h1 class="page-title">创建拍品</h1>

      <form @submit.prevent="handleSubmit" class="item-form">
        <div class="form-group">
          <label for="title">标题 *</label>
          <input
            id="title"
            v-model="form.title"
            type="text"
            class="form-input"
            placeholder="请输入拍品标题"
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
            placeholder="请输入拍品描述"
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
              placeholder="如：艺术品、古董等"
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
              placeholder="0.00"
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
              placeholder="可选"
              :disabled="loading"
            />
          </div>

          <div class="form-group">
            <label for="image">拍品图片</label>
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

        <div class="form-row">
          <div class="form-group">
            <label for="startTime">开始时间</label>
            <input
              id="startTime"
              v-model="form.startTime"
              type="datetime-local"
              class="form-input"
              :disabled="loading"
            />
          </div>

          <div class="form-group">
            <label for="endTime">结束时间</label>
            <input
              id="endTime"
              v-model="form.endTime"
              type="datetime-local"
              class="form-input"
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
            {{ loading ? '创建中...' : '创建拍品' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.create-item-page {
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
