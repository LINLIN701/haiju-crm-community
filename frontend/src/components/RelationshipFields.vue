<script setup lang="ts">
import { computed } from 'vue'
import type { RelationshipProfile } from '../types'
import { entityTypes, industryScenes, relationshipStages, relationshipTypes, sceneHint } from '../relationship'

const model = defineModel<RelationshipProfile>({ required: true })
const selectedIndustry = computed({
  get: () => industryScenes.some(item => item.name === model.value.industry) ? model.value.industry : '自定义行业',
  set: (value: string) => { model.value.industry = value === '自定义行业' ? '' : value },
})
</script>

<template>
  <label>对象类型<select v-model="model.entityType"><option v-if="!entityTypes.includes(model.entityType)">{{ model.entityType }}</option><option v-for="item in entityTypes" :key="item">{{ item }}</option></select></label>
  <label>行业场景<select v-model="selectedIndustry"><option v-for="item in industryScenes" :key="item.name">{{ item.name }}</option><option>自定义行业</option></select></label>
  <label v-if="selectedIndustry === '自定义行业'" class="full">自定义行业名称<input v-model="model.industry" maxlength="60" placeholder="填写你的行业，未知可留空" /></label>
  <p class="field-hint full"><strong>填写提示（非 AI）：</strong>{{ sceneHint(model.industry) }}</p>
  <details class="full form-disclosure">
    <summary>关系资料：机构、职务、阶段与关注事项</summary>
    <div class="form-grid">
      <label>所属机构<input v-model="model.organization" maxlength="200" placeholder="个人所在机构，或机构档案的关联单位" /></label>
      <label>职务<input v-model="model.jobTitle" maxlength="100" /></label>
      <label>邮箱<input v-model="model.email" type="email" maxlength="200" /></label>
      <label>关系类型<select v-model="model.relationshipType"><option v-if="!relationshipTypes.includes(model.relationshipType)">{{ model.relationshipType }}</option><option v-for="item in relationshipTypes" :key="item">{{ item }}</option></select></label>
      <label>关系阶段<select v-model="model.stage"><option v-if="!relationshipStages.includes(model.stage)">{{ model.stage }}</option><option v-for="item in relationshipStages" :key="item">{{ item }}</option></select></label>
      <label class="full">关注事项<textarea v-model="model.needs" maxlength="2000" rows="3" placeholder="只记录已确认的需求、共同事项和下一步；具体提醒时间在跟进记录中安排" /></label>
    </div>
  </details>
</template>
