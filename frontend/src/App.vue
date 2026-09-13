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
        <span><strong>海聚客户管理系统</strong><small>通用社区版 V1.01.01</small></span>
      </RouterLink>
      <button v-if="loggedIn" class="text-button" type="button" @click="logout">退出</button>
    </header>
    <div v-if="loggedIn" class="workspace">
      <nav class="sidebar" aria-label="主导航">
        <RouterLink to="/dashboard">关系概览</RouterLink>
        <RouterLink to="/customers">客户档案</RouterLink>
        <RouterLink to="/ai-import">AI 资料录入</RouterLink>
        <RouterLink to="/operation-logs">操作日志</RouterLink>
        <p>面向各行业的关系维护。个人、机构、客户与合作伙伴共用跟进记录。社区版仅提供单管理员权限范围。</p>
      </nav>
      <main class="content"><RouterView /></main>
    </div>
    <main v-else class="login-content"><RouterView /></main>
  </div>
</template>
