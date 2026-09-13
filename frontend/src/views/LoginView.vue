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
    <p class="eyebrow">妙海聚 · 跨行业关系维护</p>
    <h1>记住每一段关系，接好下一次联系</h1>
    <p class="muted">适用于保险、银行、地产、企业服务等行业的基础客户与合作关系维护，不以零售消费为前提。</p>
    <p class="muted">管理员密码由部署者通过环境变量设置，系统没有默认生产密码。</p>
    <form class="form-stack" @submit.prevent="login">
      <label>管理员账号<input v-model="form.username" autocomplete="username" required /></label>
      <label>管理员密码<input v-model="form.password" type="password" autocomplete="current-password" required /></label>
      <p v-if="error" class="message error">{{ error }}</p>
      <button class="primary" :disabled="submitting">{{ submitting ? '正在验证…' : '进入系统' }}</button>
    </form>
  </section>
</template>
