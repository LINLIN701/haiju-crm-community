<script setup lang="ts">
import { reactive, ref } from 'vue'
import { api } from '../api/client'
import type { CustomerDraft } from '../types'
import RelationshipFields from '../components/RelationshipFields.vue'
import { emptyCustomer, emptyRelationship } from '../relationship'

interface ParseResult {
  draft: CustomerDraft
  callLogId: number
  provider: string
  model: string
  confirmationNotice: string
}

const sourceText = ref('')
const result = ref<ParseResult | null>(null)
const draft = reactive<CustomerDraft>(emptyCustomer())
const parsing = ref(false)
const saving = ref(false)
const error = ref('')
const message = ref('')

async function parse() {
  parsing.value = true
  error.value = ''
  message.value = ''
  result.value = null
  try {
    const parsed = await api.post<ParseResult>('/api/v1/ai/parse-customer', { text: sourceText.value })
    result.value = parsed
    Object.assign(draft, parsed.draft, { relationship: { ...emptyRelationship(), ...parsed.draft.relationship } })
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : 'AI 解析失败'
  } finally {
    parsing.value = false
  }
}

async function confirmSave() {
  saving.value = true
  error.value = ''
  try {
    await api.post('/api/v1/customers', draft)
    message.value = '人工确认后的客户资料已保存。'
    result.value = null
    sourceText.value = ''
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '保存失败'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section class="page-heading"><div><p class="eyebrow">真实外部模型 · 人工确认</p><h1>AI 客户资料录入</h1></div></section>
  <section class="notice"><strong>真实性边界</strong><p>系统只调用部署者配置的外部模型，不提供本地规则兜底。模型未配置、调用失败或结果无效时会明确失败，解析结果不会自动写入客户库。</p></section>
  <p class="field-hint">可整理各行业个人或机构的已知联系资料。不要提交健康、征信、账户、证件等敏感资料；不生成核保、授信或投资判断。</p>
  <p v-if="error" class="message error" role="alert">{{ error }}</p>
  <p v-if="message" class="message success" role="status">{{ message }}</p>
  <section class="panel">
    <form class="form-stack" @submit.prevent="parse">
      <label>粘贴客户资料<textarea v-model="sourceText" rows="8" maxlength="20000" required placeholder="例如：陈女士（虚构），企业服务行业，示例机构的项目联系人，希望下周沟通合作范围。未知信息不要补写。" /></label>
      <button class="primary" :disabled="parsing">{{ parsing ? '正在调用外部模型…' : '开始 AI 解析' }}</button>
    </form>
  </section>
  <section v-if="result" class="panel">
    <div class="section-title"><div><p class="eyebrow">调用日志 #{{ result.callLogId }}</p><h2>请人工核对后保存</h2></div><span>{{ result.provider }} / {{ result.model }}</span></div>
    <form class="form-grid" @submit.prevent="confirmSave">
      <label>姓名或机构名称<input v-model="draft.name" required maxlength="100" /></label>
      <label>手机号<input v-model="draft.phone" maxlength="32" /></label>
      <label>微信<input v-model="draft.wechat" maxlength="100" /></label>
      <label>标签<input v-model="draft.tags" maxlength="500" /></label>
      <RelationshipFields v-model="draft.relationship" />
      <label class="full">备注<textarea v-model="draft.notes" rows="4" maxlength="5000" /></label>
      <button class="primary" :disabled="saving">{{ saving ? '保存中…' : '确认并保存客户' }}</button>
    </form>
  </section>
</template>
