import { createRouter, createWebHistory } from 'vue-router'
import { hasCredentials } from './api/client'
import AiImportView from './views/AiImportView.vue'
import CustomerDetailView from './views/CustomerDetailView.vue'
import CustomersView from './views/CustomersView.vue'
import DashboardView from './views/DashboardView.vue'
import LoginView from './views/LoginView.vue'
import OperationLogsView from './views/OperationLogsView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: LoginView },
    { path: '/', redirect: '/dashboard' },
    { path: '/dashboard', component: DashboardView, meta: { auth: true } },
    { path: '/customers', component: CustomersView, meta: { auth: true } },
    { path: '/customers/:id', component: CustomerDetailView, meta: { auth: true } },
    { path: '/ai-import', component: AiImportView, meta: { auth: true } },
    { path: '/operation-logs', component: OperationLogsView, meta: { auth: true } },
  ],
})

router.beforeEach((to) => {
  if (to.meta.auth && !hasCredentials()) return '/login'
  if (to.path === '/login' && hasCredentials()) return '/dashboard'
})
