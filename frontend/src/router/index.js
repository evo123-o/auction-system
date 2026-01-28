import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '../stores/auth';

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue')
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue')
  },
  {
    path: '/items/:id',
    name: 'ItemDetail',
    component: () => import('../views/ItemDetail.vue')
  },
  {
    path: '/items/create',
    name: 'CreateItem',
    component: () => import('../views/CreateItem.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/items/:id/edit',
    name: 'EditItem',
    component: () => import('../views/EditItem.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('../views/Dashboard.vue'),
    meta: { requiresAuth: true }
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

// Navigation guard for authentication
router.beforeEach((to, from, next) => {
  const authStore = useAuthStore();
  
  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    next({ name: 'Login', query: { redirect: to.fullPath } });
  } else {
    next();
  }
});

export default router;
