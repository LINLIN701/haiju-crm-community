<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api/client'
import type { Overview, Reminder } from '../types'

const overview = ref<Overview | null>(null)
const reminders = ref<Reminder[]>([])
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    ;[overview.value, reminders.value] = await Promise.all([
      api.get<Overview>('/api/v1/dashboard/overview'),
      api.get<Reminder[]>('/api/v1/notifications'),
    ])
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="page-heading">
    <div><p class="eyebrow">今天先处理什么</p><h1>经营概览</h1></div>
    <button class="secondary" type="button" @click="load">刷新</button>
  </section>
  <p v-if="loading" class="state">正在加载真实数据…</p>
  <p v-else-if="error" class="message error">{{ error }} <button class="text-button" @click="load">重试</button></p>
  <template v-else-if="overview">
    <section class="metric-grid">
      <article><span>客户总数</span><strong>{{ overview.customerCount }}</strong><small>已保存客户档案</small></article>
      <article><span>到期跟进</span><strong>{{ overview.dueFollowUps }}</strong><small>需要尽快处理</small></article>
      <article><span>累计消费</span><strong>¥{{ Number(overview.totalConsumption).toFixed(2) }}</strong><small>真实消费记录汇总</small></article>
    </section>
    <section class="panel">
      <div class="section-title"><div><p class="eyebrow">下一步</p><h2>到期跟进</h2></div><RouterLink to="/customers">查看客户</RouterLink></div>
      <p v-if="reminders.length === 0" class="empty">当前没有到期跟进。为客户记录下一次跟进时间后，会自动出现在这里。</p>
      <div v-else class="list">
        <RouterLink v-for="item in reminders" :key="`${item.customerId}-${item.dueAt}`" :to="`/customers/${item.customerId}`" class="list-row">
          <span><strong>{{ item.customerName }}</strong><small>{{ item.lastSummary }}</small></span>
          <time>{{ item.dueAt.replace('T', ' ') }}</time>
        </RouterLink>
      </div>
    </section>
  </template>
</template>
