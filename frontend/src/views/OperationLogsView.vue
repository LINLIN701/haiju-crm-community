<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api/client'
import type { OperationLog } from '../types'

const logs = ref<OperationLog[]>([])
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true
  try {
    logs.value = await api.get('/api/v1/operation-logs')
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="page-heading"><div><p class="eyebrow">可追溯</p><h1>操作日志</h1></div><button class="secondary" @click="load">刷新</button></section>
  <p v-if="loading" class="state">正在加载…</p>
  <p v-else-if="error" class="message error">{{ error }}</p>
  <section v-else class="panel">
    <p v-if="logs.length === 0" class="empty">暂无操作记录。</p>
    <div v-else class="list"><article v-for="item in logs" :key="item.id" class="list-row"><span><strong>{{ item.action }}</strong><small>{{ item.detail || item.targetType }}</small></span><time>{{ item.createdAt.replace('T', ' ') }}</time></article></div>
  </section>
</template>
