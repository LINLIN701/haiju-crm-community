<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, clearCredentials, setCredentials } from '../api/client'

const router = useRouter()
const form = reactive({ username: 'admin', password: '' })
const submitting = ref(false)
const error = ref('')

async function login() {
  submitting.value = true
  error.value = ''
  setCredentials(form.username.trim(), form.password)
  try {
    await api.get('/api/v1/customers')
    await router.push('/dashboard')
  } catch (reason) {
    clearCredentials()
    error.value = reason instanceof Error ? reason.message : '登录失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="login-card">
    <p class="eyebrow">妙海聚 · 自托管社区版</p>
    <h1>把客户资料和下一次跟进，真正留在自己的系统里</h1>
    <p class="muted">管理员密码由部署者通过环境变量设置，系统没有默认生产密码。</p>
    <form class="form-stack" @submit.prevent="login">
      <label>管理员账号<input v-model="form.username" autocomplete="username" required /></label>
      <label>管理员密码<input v-model="form.password" type="password" autocomplete="current-password" required /></label>
      <p v-if="error" class="message error">{{ error }}</p>
      <button class="primary" :disabled="submitting">{{ submitting ? '正在验证…' : '进入系统' }}</button>
    </form>
  </section>
</template>
