<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api/client'
import type { Consumption, ContactRecord, Customer } from '../types'
import RelationshipFields from '../components/RelationshipFields.vue'
import { emptyCustomer, emptyRelationship, sceneHint } from '../relationship'

const route = useRoute()
const id = Number(route.params.id)
const customer = ref<Customer | null>(null)
const contacts = ref<ContactRecord[]>([])
const nextContactAt = computed(() => contacts.value.reduce<ContactRecord | null>((latest, item) => !latest || item.id > latest.id ? item : latest, null)?.nextContactAt)
const consumptions = ref<Consumption[]>([])
const loading = ref(true)
const error = ref('')
const message = ref('')
const savingProfile = ref(false)
const profileDraft = reactive(emptyCustomer())
const savingContact = ref(false)
const savingConsumption = ref(false)
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
      relationship: { ...emptyRelationship(), ...customer.value.relationship },
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
  if (savingContact.value) return
  savingContact.value = true
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
  } finally {
    savingContact.value = false
  }
}

async function addConsumption() {
  if (savingConsumption.value) return
  savingConsumption.value = true
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
  } finally {
    savingConsumption.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="page-heading"><div><RouterLink to="/customers">← 返回客户</RouterLink><h1>{{ customer?.name || '客户详情' }}</h1></div></section>
  <p v-if="loading" class="state">正在加载真实客户资料…</p>
  <p v-if="error" class="message error" role="alert">{{ error }} <button v-if="!customer" class="text-button" @click="load">重试</button></p>
  <template v-if="!loading && customer">
    <p v-if="message" class="message success" role="status">{{ message }}</p>
    <section class="notice">
      <strong>{{ customer.relationship.industry }} · {{ customer.relationship.relationshipType }} · {{ customer.relationship.stage }}</strong>
      <p>{{ customer.relationship.organization || '未填写所属机构' }}<template v-if="customer.relationship.jobTitle"> · {{ customer.relationship.jobTitle }}</template></p>
      <p>关注事项：{{ customer.relationship.needs || '暂未记录，可展开客户资料补充。' }}</p>
      <p v-if="nextContactAt">下一次联系：{{ nextContactAt.replace('T', ' ') }}</p>
      <p v-else>尚未安排下一次联系，可在下方保存跟进时设置。</p>
    </section>
    <details class="panel profile-editor">
      <summary>客户资料与关系信息</summary>
      <form class="form-grid" @submit.prevent="saveProfile">
        <label>姓名或机构名称<input v-model="profileDraft.name" required maxlength="100" /></label>
        <label>手机号<input v-model="profileDraft.phone" maxlength="32" /></label>
        <label>微信<input v-model="profileDraft.wechat" maxlength="100" /></label>
        <label>标签<input v-model="profileDraft.tags" maxlength="500" /></label>
        <RelationshipFields v-model="profileDraft.relationship" />
        <label class="full">备注<textarea v-model="profileDraft.notes" rows="3" maxlength="5000" /></label>
        <button class="secondary" :disabled="savingProfile">{{ savingProfile ? '更新中…' : '更新客户资料' }}</button>
      </form>
    </details>
    <section>
      <article class="panel">
        <h2>沟通与下一次联系</h2>
        <p class="field-hint">{{ sceneHint(customer.relationship.industry) }}</p>
        <form class="form-stack" @submit.prevent="addContact">
          <label>跟进时间<input v-model="contact.contactAt" type="datetime-local" required /></label>
          <label>渠道<select v-model="contact.channel"><option>微信</option><option>电话</option><option>邮件</option><option>面谈</option><option>上门拜访</option><option>视频会议</option><option>到店</option><option>其他</option></select></label>
          <label>本次摘要<textarea v-model="contact.summary" rows="3" maxlength="1000" required /></label>
          <label>下次跟进<input v-model="contact.nextContactAt" type="datetime-local" /></label>
          <button class="primary" :disabled="savingContact">{{ savingContact ? '保存中…' : '保存跟进' }}</button>
        </form>
        <p v-if="contacts.length === 0" class="empty">还没有跟进记录。</p>
        <div v-else class="timeline"><article v-for="item in contacts" :key="item.id"><strong>{{ item.channel }} · {{ item.contactAt.replace('T', ' ') }}</strong><p>{{ item.summary }}</p><small v-if="item.nextContactAt">下次：{{ item.nextContactAt.replace('T', ' ') }}</small></article></div>
      </article>
      <details class="panel optional-consumption">
        <summary>可选：消费记录（{{ consumptions.length }}条）</summary>
        <p class="field-hint">仅记录实际消费。保险保额、银行资产余额、房产意向总价不属于消费金额；不需要记账的关系可以完全不用此项。</p>
        <form class="form-stack" @submit.prevent="addConsumption">
          <label>消费时间<input v-model="consumption.occurredAt" type="datetime-local" required /></label>
          <label>金额<input v-model.number="consumption.amount" type="number" min="0" step="0.01" required /></label>
          <label>项目<input v-model="consumption.itemName" maxlength="200" required /></label>
          <label>备注<textarea v-model="consumption.note" rows="2" maxlength="1000" /></label>
          <button class="primary" :disabled="savingConsumption">{{ savingConsumption ? '保存中…' : '保存消费' }}</button>
        </form>
        <p v-if="consumptions.length === 0" class="empty">还没有消费记录。</p>
        <div v-else class="timeline"><article v-for="item in consumptions" :key="item.id"><strong>{{ item.itemName }} · ¥{{ Number(item.amount).toFixed(2) }}</strong><p>{{ item.occurredAt.replace('T', ' ') }}</p><small>{{ item.note || '无备注' }}</small></article></div>
      </details>
    </section>
  </template>
</template>
