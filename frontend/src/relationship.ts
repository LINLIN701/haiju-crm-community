import type { CustomerDraft, RelationshipProfile } from './types'

// Static form guidance, not AI output or an assertion about a customer's situation.
export const industryScenes = [
  { name: '通用关系维护', hint: '记录联系缘由、共同事项、已约定的下一步。适用于客户、合作伙伴、转介绍人与其他长期联系人。' },
  { name: '保险', hint: '记录已表达的保障咨询、续期回访约定、服务事项与下一次沟通。不要在备注中收集病历、证件或银行卡资料；这里不办理核保与理赔。' },
  { name: '银行', hint: '记录个人或企业客户的服务需求、材料沟通、合作进展和回访约定。不要录入账号余额、征信或交易明细；这里不办理授信、支付或适当性判断。' },
  { name: '房地产', hint: '区分购房、租赁、业主或合作渠道，记录已表达的区域需求、带看反馈、委托沟通和回访时间。这里不是房源、合同或资金监管系统。' },
  { name: '企业服务', hint: '记录所属企业、联系人职务、采购需求、方案沟通、合作进展与服务回访。机构名称是资料字段，不是自动建立的企业关系图谱。' },
  { name: '教育培训', hint: '记录课程咨询、预约沟通、学习服务与续学回访约定。不录入未成年人的敏感资料；这里不提供教务或招生审批。' },
  { name: '专业服务', hint: '适用于咨询、设计、会计等服务关系：记录项目诉求、服务范围、沟通结论和回访。不把机密案卷或敏感材料存入备注。' },
  { name: '零售', hint: '记录商品偏好、售后需求与回访约定。确需记账时，可展开客户详情中的可选消费记录；关系维护不要求发生消费。' },
]

export const relationshipTypes = ['未分类', '潜在客户', '客户', '合作伙伴', '转介绍人', '供应商', '其他联系人']
export const relationshipStages = ['未标注', '初次接触', '需求沟通', '方案跟进', '合作服务', '持续维护', '暂缓联系']
export const entityTypes = ['未分类', '个人', '机构']

export function emptyRelationship(): RelationshipProfile {
  return { entityType: '未分类', industry: '通用关系维护', organization: '', jobTitle: '', email: '', relationshipType: '未分类', stage: '未标注', needs: '' }
}

export function emptyCustomer(): CustomerDraft {
  return { name: '', phone: '', wechat: '', tags: '', notes: '', relationship: emptyRelationship() }
}

export function sceneHint(industry: string): string {
  return (industryScenes.find(scene => scene.name === industry) ?? industryScenes[0]!).hint
}
