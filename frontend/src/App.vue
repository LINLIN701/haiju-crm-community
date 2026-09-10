<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { clearCredentials, hasCredentials } from './api/client'

const route = useRoute()
const router = useRouter()
const loggedIn = computed(() => route.path !== '/login' && hasCredentials())

function logout() {
  clearCredentials()
  router.push('/login')
}
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <RouterLink class="brand" to="/dashboard">
        <span class="brand-mark">海</span>
        <span><strong>海聚客户管理系统</strong><small>社区版 V1.00.01</small></span>
      </RouterLink>
      <button v-if="loggedIn" class="text-button" type="button" @click="logout">退出</button>
    </header>
    <div v-if="loggedIn" class="workspace">
      <nav class="sidebar" aria-label="主导航">
        <RouterLink to="/dashboard">经营概览</RouterLink>
        <RouterLink to="/customers">客户档案</RouterLink>
        <RouterLink to="/ai-import">AI 资料录入</RouterLink>
        <RouterLink to="/operation-logs">操作日志</RouterLink>
        <p>社区版为单管理员自托管内核。团队协同、会员、业绩和自动化属于商业版。</p>
      </nav>
      <main class="content"><RouterView /></main>
    </div>
    <main v-else class="login-content"><RouterView /></main>
  </div>
</template>
