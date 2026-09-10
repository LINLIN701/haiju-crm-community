<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { api, download } from '../api/client'
import type { Customer, CustomerDraft } from '../types'

const customers = ref<Customer[]>([])
const keyword = ref('')
const loading = ref(true)
const submitting = ref(false)
const error = ref('')
const message = ref('')
const file = ref<File | null>(null)
const draft = reactive<CustomerDraft>({ name: '', phone: '', wechat: '', tags: '', notes: '' })

async function load() {
  loading.value = true
  error.value = ''
  try {
    customers.value = await api.get(`/api/v1/customers?keyword=${encodeURIComponent(keyword.value.trim())}`)
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '加载失败'
  } finally {
    loading.value = false
  }
}

async function createCustomer() {
  submitting.value = true
  error.value = ''
  message.value = ''
  try {
    await api.post('/api/v1/customers', draft)
    Object.assign(draft, { name: '', phone: '', wechat: '', tags: '', notes: '' })
    message.value = '客户已保存。'
    await load()
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '保存失败'
  } finally {
    submitting.value = false
  }
}

async function remove(customer: Customer) {
  if (!confirm(`确定删除客户“${customer.name}”及其跟进和消费记录吗？`)) return
  try {
    await api.delete(`/api/v1/customers/${customer.id}`)
    await load()
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '删除失败'
  }
}

async function importCsv() {
  if (!file.value) return
  const form = new FormData()
  form.append('file', file.value)
  try {
    const result = await api.post<{ totalRows: number; createdRows: number; skippedRows: number }>('/api/v1/customers/import', form)
    message.value = `导入完成：新增 ${result.createdRows}，跳过 ${result.skippedRows}。`
    file.value = null
    await load()
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '导入失败'
  }
}

onMounted(load)
</script>

<template>
  <section class="page-heading"><div><p class="eyebrow">客户事实库</p><h1>客户档案</h1></div><RouterLink class="primary link-button" to="/ai-import">AI 资料录入</RouterLink></section>
  <p v-if="error" class="message error">{{ error }}</p>
  <p v-if="message" class="message success">{{ message }}</p>
  <section class="panel">
    <h2>新增客户</h2>
    <form class="form-grid" @submit.prevent="createCustomer">
      <label>姓名<input v-model="draft.name" required maxlength="100" /></label>
      <label>手机号<input v-model="draft.phone" maxlength="32" /></label>
      <label>微信<input v-model="draft.wechat" maxlength="100" /></label>
      <label>标签<input v-model="draft.tags" maxlength="500" placeholder="重点，老客户" /></label>
      <label class="full">备注<textarea v-model="draft.notes" rows="3" maxlength="5000" /></label>
      <button class="primary" :disabled="submitting">{{ submitting ? '保存中…' : '保存客户' }}</button>
    </form>
  </section>
  <section class="panel">
    <div class="toolbar">
      <form class="search" @submit.prevent="load"><input v-model="keyword" placeholder="姓名、手机号、微信或标签" /><button class="secondary">搜索</button></form>
      <button class="secondary" type="button" @click="download('/api/v1/customers/export', 'haiju-customers.csv')">导出 CSV</button>
    </div>
    <div class="import-row">
      <span>CSV 列名：姓名、手机号、微信、标签、备注</span>
      <input type="file" accept=".csv,text/csv" @change="file = ($event.target as HTMLInputElement).files?.[0] ?? null" />
      <button class="secondary" :disabled="!file" @click="importCsv">导入</button>
    </div>
    <p v-if="loading" class="state">正在加载…</p>
    <p v-else-if="customers.length === 0" class="empty">还没有客户。可以手工新增、CSV 导入或使用 AI 资料录入。</p>
    <div v-else class="list">
      <article v-for="customer in customers" :key="customer.id" class="list-row customer-row">
        <RouterLink :to="`/customers/${customer.id}`"><strong>{{ customer.name }}</strong><small>{{ customer.phone || '未填写手机' }} · {{ customer.tags || '暂无标签' }}</small></RouterLink>
        <button class="danger-text" type="button" @click="remove(customer)">删除</button>
      </article>
    </div>
  </section>
</template>
