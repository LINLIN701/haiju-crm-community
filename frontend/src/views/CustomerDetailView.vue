<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api/client'
import type { Consumption, ContactRecord, Customer } from '../types'

const route = useRoute()
const id = Number(route.params.id)
const customer = ref<Customer | null>(null)
const contacts = ref<ContactRecord[]>([])
const consumptions = ref<Consumption[]>([])
const loading = ref(true)
const error = ref('')
const message = ref('')
const savingProfile = ref(false)
const profileDraft = reactive({ name: '', phone: '', wechat: '', tags: '', notes: '' })
const contact = reactive({ contactAt: localNow(), channel: '微信', summary: '', nextContactAt: '' })
const consumption = reactive({ occurredAt: localNow(), amount: 0, itemName: '', note: '' })

function localNow() {
  const date = new Date(Date.now() - new Date().getTimezoneOffset() * 60_000)
  return date.toISOString().slice(0, 16)
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    ;[customer.value, contacts.value, consumptions.value] = await Promise.all([
      api.get<Customer>(`/api/v1/customers/${id}`),
      api.get<ContactRecord[]>(`/api/v1/customers/${id}/contacts`),
      api.get<Consumption[]>(`/api/v1/customers/${id}/consumptions`),
    ])
    Object.assign(profileDraft, {
      name: customer.value.name,
      phone: customer.value.phone || '',
      wechat: customer.value.wechat || '',
      tags: customer.value.tags || '',
      notes: customer.value.notes || '',
    })
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '加载失败'
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  savingProfile.value = true
  error.value = ''
  try {
    customer.value = await api.put<Customer>(`/api/v1/customers/${id}`, profileDraft)
    message.value = '客户资料已更新。'
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '更新失败'
  } finally {
    savingProfile.value = false
  }
}

async function addContact() {
  error.value = ''
  try {
    await api.post(`/api/v1/customers/${id}/contacts`, {
      ...contact,
      contactAt: contact.contactAt + ':00',
      nextContactAt: contact.nextContactAt ? contact.nextContactAt + ':00' : null,
    })
    contact.summary = ''
    contact.nextContactAt = ''
    message.value = '跟进记录已保存。'
    await load()
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '保存失败'
  }
}

async function addConsumption() {
  error.value = ''
  try {
    await api.post(`/api/v1/customers/${id}/consumptions`, {
      ...consumption,
      occurredAt: consumption.occurredAt + ':00',
    })
    consumption.amount = 0
    consumption.itemName = ''
    consumption.note = ''
    message.value = '消费记录已保存。'
    await load()
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '保存失败'
  }
}

onMounted(load)
</script>

<template>
  <section class="page-heading"><div><RouterLink to="/customers">← 返回客户</RouterLink><h1>{{ customer?.name || '客户详情' }}</h1></div></section>
  <p v-if="loading" class="state">正在加载真实客户资料…</p>
  <p v-else-if="error" class="message error">{{ error }}</p>
  <template v-else-if="customer">
    <p v-if="message" class="message success">{{ message }}</p>
    <section class="panel">
      <h2>客户资料</h2>
      <form class="form-grid" @submit.prevent="saveProfile">
        <label>姓名<input v-model="profileDraft.name" required maxlength="100" /></label>
        <label>手机号<input v-model="profileDraft.phone" maxlength="32" /></label>
        <label>微信<input v-model="profileDraft.wechat" maxlength="100" /></label>
        <label>标签<input v-model="profileDraft.tags" maxlength="500" /></label>
        <label class="full">备注<textarea v-model="profileDraft.notes" rows="3" maxlength="5000" /></label>
        <button class="secondary" :disabled="savingProfile">{{ savingProfile ? '更新中…' : '更新客户资料' }}</button>
      </form>
    </section>
    <section class="two-column">
      <article class="panel">
        <h2>记录跟进</h2>
        <form class="form-stack" @submit.prevent="addContact">
          <label>跟进时间<input v-model="contact.contactAt" type="datetime-local" required /></label>
          <label>渠道<select v-model="contact.channel"><option>微信</option><option>电话</option><option>到店</option><option>其他</option></select></label>
          <label>本次摘要<textarea v-model="contact.summary" rows="3" maxlength="1000" required /></label>
          <label>下次跟进<input v-model="contact.nextContactAt" type="datetime-local" /></label>
          <button class="primary">保存跟进</button>
        </form>
        <p v-if="contacts.length === 0" class="empty">还没有跟进记录。</p>
        <div v-else class="timeline"><article v-for="item in contacts" :key="item.id"><strong>{{ item.channel }} · {{ item.contactAt.replace('T', ' ') }}</strong><p>{{ item.summary }}</p><small v-if="item.nextContactAt">下次：{{ item.nextContactAt.replace('T', ' ') }}</small></article></div>
      </article>
      <article class="panel">
        <h2>记录消费</h2>
        <form class="form-stack" @submit.prevent="addConsumption">
          <label>消费时间<input v-model="consumption.occurredAt" type="datetime-local" required /></label>
          <label>金额<input v-model.number="consumption.amount" type="number" min="0" step="0.01" required /></label>
          <label>项目<input v-model="consumption.itemName" maxlength="200" required /></label>
          <label>备注<textarea v-model="consumption.note" rows="2" maxlength="1000" /></label>
          <button class="primary">保存消费</button>
        </form>
        <p v-if="consumptions.length === 0" class="empty">还没有消费记录。</p>
        <div v-else class="timeline"><article v-for="item in consumptions" :key="item.id"><strong>{{ item.itemName }} · ¥{{ Number(item.amount).toFixed(2) }}</strong><p>{{ item.occurredAt.replace('T', ' ') }}</p><small>{{ item.note || '无备注' }}</small></article></div>
      </article>
    </section>
  </template>
</template>
