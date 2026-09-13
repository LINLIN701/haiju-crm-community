<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { api, download } from '../api/client'
import type { Customer, CustomerDraft } from '../types'
import RelationshipFields from '../components/RelationshipFields.vue'
import { emptyCustomer, industryScenes, relationshipStages } from '../relationship'

const customers = ref<Customer[]>([])
const keyword = ref('')
const industry = ref('')
const stage = ref('')
const importing = ref(false)
const exporting = ref(false)
const loading = ref(true)
const submitting = ref(false)
const error = ref('')
const message = ref('')
const file = ref<File | null>(null)
const draft = reactive<CustomerDraft>(emptyCustomer())
let loadSequence = 0

async function load() {
  const sequence = ++loadSequence
  loading.value = true
  error.value = ''
  try {
    const query = new URLSearchParams({ keyword: keyword.value.trim(), industry: industry.value.trim(), stage: stage.value })
    const result = await api.get<Customer[]>(`/api/v1/customers?${query}`)
    if (sequence === loadSequence) customers.value = result
  } catch (reason) {
    if (sequence === loadSequence) error.value = reason instanceof Error ? reason.message : '加载失败'
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

async function createCustomer() {
  submitting.value = true
  error.value = ''
  message.value = ''
  try {
    await api.post('/api/v1/customers', draft)
    Object.assign(draft, emptyCustomer())
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
  if (!file.value || importing.value) return
  importing.value = true
  error.value = ''
  message.value = ''
  const form = new FormData()
  form.append('file', file.value)
  try {
    const result = await api.post<{ totalRows: number; createdRows: number; skippedRows: number }>('/api/v1/customers/import', form)
    message.value = `导入完成：新增 ${result.createdRows}，跳过 ${result.skippedRows}。`
    file.value = null
    await load()
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '导入失败'
  } finally {
    importing.value = false
  }
}

async function exportCsv() {
  if (exporting.value) return
  exporting.value = true
  error.value = ''
  try { await download('/api/v1/customers/export', 'haiju-customers.csv') }
  catch (reason) { error.value = reason instanceof Error ? reason.message : '导出失败' }
  finally { exporting.value = false }
}

onMounted(load)
</script>

<template>
  <section class="page-heading"><div><p class="eyebrow">客户事实库</p><h1>客户档案</h1></div><RouterLink class="primary link-button" to="/ai-import">AI 资料录入</RouterLink></section>
  <p class="muted">统一维护个人、机构、客户与合作关系。行业用于分类和填写提示，不改变已有资料。</p>
  <p v-if="error" class="message error" role="alert">{{ error }}</p>
  <p v-if="message" class="message success" role="status">{{ message }}</p>
  <section class="panel">
    <h2>新增客户</h2>
    <form class="form-grid" @submit.prevent="createCustomer">
      <label>姓名或机构名称<input v-model="draft.name" required maxlength="100" /></label>
      <label>手机号<input v-model="draft.phone" maxlength="32" /></label>
      <label>微信<input v-model="draft.wechat" maxlength="100" /></label>
      <label>标签<input v-model="draft.tags" maxlength="500" placeholder="待回访，合作渠道" /></label>
      <RelationshipFields v-model="draft.relationship" />
      <label class="full">备注<textarea v-model="draft.notes" rows="3" maxlength="5000" /></label>
      <button class="primary" :disabled="submitting">{{ submitting ? '保存中…' : '保存客户' }}</button>
    </form>
  </section>
  <section class="panel">
    <div class="toolbar">
      <form class="search" @submit.prevent="load"><input v-model="keyword" aria-label="搜索客户" placeholder="姓名、机构、联系方式、标签或关注事项" /><button class="secondary">搜索</button></form>
      <button class="secondary" type="button" :disabled="exporting" @click="exportCsv">{{ exporting ? '导出中…' : '导出 CSV' }}</button>
    </div>
    <form class="filter-row" @submit.prevent="load">
      <label>筛选行业<input v-model="industry" list="industry-filter-options" placeholder="全部行业，也可填写自定义行业" maxlength="60" /><datalist id="industry-filter-options"><option v-for="item in industryScenes" :key="item.name" :value="item.name" /></datalist></label>
      <label>筛选阶段<select v-model="stage"><option value="">全部阶段</option><option v-for="item in relationshipStages" :key="item">{{ item }}</option></select></label>
      <button class="secondary">应用筛选</button>
    </form>
    <p class="field-hint">筛选匹配结果最多500条；CSV仍导出全部行业最近新增的500条，不跟随筛选。</p>
    <div class="import-row">
      <span>CSV保留“姓名”列；支持行业、机构、关系阶段等新列，旧模板也可用。</span>
      <label class="file-picker">选择CSV文件<input type="file" accept=".csv,text/csv" :disabled="importing" @change="file = ($event.target as HTMLInputElement).files?.[0] ?? null" />{{ file?.name || '尚未选择文件' }}</label>
      <button class="secondary" :disabled="!file || importing" @click="importCsv">{{ importing ? '导入中…' : '导入' }}</button>
    </div>
    <p v-if="loading" class="state">正在加载…</p>
    <p v-else-if="customers.length === 0" class="empty">没有匹配的档案。可以调整搜索和筛选，或新增、导入资料。</p>
    <div v-else class="list">
      <article v-for="customer in customers" :key="customer.id" class="list-row customer-row">
        <RouterLink :to="`/customers/${customer.id}`"><strong>{{ customer.name }}</strong><small>{{ customer.relationship.industry }} · {{ customer.relationship.relationshipType }} · {{ customer.relationship.stage }}</small><small>{{ customer.relationship.organization || customer.phone || '未填写机构或电话' }} · {{ customer.tags || '暂无标签' }}</small></RouterLink>
        <button class="danger-text" type="button" @click="remove(customer)">删除</button>
      </article>
    </div>
  </section>
</template>
