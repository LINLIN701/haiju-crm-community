# 海聚客户管理系统社区版

海聚客户管理系统社区版是妙海聚提供的**跨行业、自托管关系维护 CRM**。围绕个人与机构档案、客户与合作关系、沟通记录、关注事项和下一次联系，服务保险、银行、房地产、企业服务、教育培训、专业服务、零售及其他需要长期维系关系的场景。使用它不要求客户发生消费。

[![CI](https://github.com/LINLIN701/haiju-crm-community/actions/workflows/ci.yml/badge.svg)](https://github.com/LINLIN701/haiju-crm-community/actions/workflows/ci.yml)

**第一次使用：** [部署教程](docs/DEPLOYMENT.md) · [带截图的使用教程](docs/USER_GUIDE.md) · [多行业应用与边界](docs/INDUSTRY_SCENARIOS.md) · [兼容旧版的练习CSV](docs/examples/customers-sample.csv)

当前版本 **V1.01.01**。新增跨行业关系字段、行业/阶段筛选、可选消费区与中文操作日志，包含V2增量迁移。旧资料保留，升级前请备份。

> 社区源码已于 2026-09-11 公开到 `LINLIN701/haiju-crm-community`，包含独立部署所需的前端、后端、数据库迁移和 Docker Compose 配置。版本与下载以 [Releases](https://github.com/LINLIN701/haiju-crm-community/releases) 为准。

## 社区版适合谁

- 需要持续维护客户、机构、合作渠道和转介绍人的个人从业者。
- 已有表格或零散沟通记录，希望统一安排回访与下一次联系的业务人员。
- 希望自行部署和掌控数据，接受单管理员权限范围的使用者。
- 开发者和实施伙伴，用于评估通用关系维护基础能力；AI为自备密钥的可选项。

## 社区版能力

- 个人/机构、行业、所属机构、职务、邮箱、关系类型、阶段与关注事项。
- 客户标签、搜索、行业/阶段筛选，手机号重复保护；CSV包含关系字段，旧模板仍可导入。
- 多渠道沟通、下一次联系、到期与未来联系统计；无需录入消费也可使用完整基础跟进链路。
- 可选消费记录，保留既有数据；不把保额、资产余额或房产意向金额当消费。
- 真实外部AI文字整理，关系信息一并人工核对后保存；未配置时明确失败。
- 中文操作日志，历史英文审计代码保留在数据库/API中，不作为用户界面标题。

行业选项提供的是**可落库的分类和静态填写提示**，不是已经实现的行业核心业务系统。社区版不包含保险核保/理赔、银行授信/支付、房源/合同交易、复杂关系图谱或多人数据隔离。详见[行业应用与边界](docs/INDUSTRY_SCENARIOS.md)。

高级 AI、自动化、多门店、业绩链、智能日报、第三方连接器、托管运维和服务保障属于商业版本。详见 [COMMERCIAL_EDITIONS.md](COMMERCIAL_EDITIONS.md)。

## 快速启动

1. 将 `.env.example` 复制为 `.env`。
2. 修改数据库和管理员强密码；不要把 `.env` 提交到仓库。
3. 在安装了 Docker Compose 的机器执行：

```bash
docker compose -p haiju up -d --build
```

4. 打开 `http://localhost:8088`，使用 `.env` 中的管理员账号和密码登录。

以上用于全新安装；已有部署必须沿用原Compose项目名，避免指向另一套数据卷。首次部署、升级和备份请完整阅读[部署教程](docs/DEPLOYMENT.md)。

生产环境必须配置 HTTPS 反向代理。HTTP Basic 只能在 HTTPS 或可信本机网络中使用。社区数据库必须是全新独立数据库，不得指向其他业务系统的生产库。

## 独立验证

使用 PowerShell 7，并准备 Node.js 22、Java 17、ripgrep（`rg`）和 Docker Compose 后运行：

```powershell
.\scripts\verify-community-source.ps1
.\scripts\verify-community-compose.ps1
```

第一条命令执行源码边界扫描、前端构建、生产依赖审计和后端测试；第二条命令使用临时 MySQL 8.4 数据卷执行空库迁移与真实 Compose 冒烟测试，并在结束后删除测试容器和数据卷。

## AI 真实性

只有 `APP_AI_BASE_URL`、`APP_AI_API_KEY` 和 `APP_AI_MODEL` 同时配置时才执行 AI 解析。系统调用 OpenAI 兼容的 `/chat/completions` 接口，并记录 provider、model、状态和日志编号。未配置、调用失败、返回非 JSON 或缺少客户姓名时都会明确失败，不会回退为本地规则、静态模板或演示数据。模型结果必须由用户确认后才能保存。

## 开源与品牌

- 社区代码采用 `AGPL-3.0-or-later`。
- “妙海聚”“海聚客户管理系统”名称、图形和字标不随代码许可证授权。
- 修改版应使用自己的名称和视觉，或事先取得妙海聚书面授权。

## 商业合作

需要专业版、企业版、独立部署、数据迁移或实施支持，可通过[商业合作表单](https://github.com/LINLIN701/haiju-crm-community/issues/new?template=commercial-inquiry.yml)申请演示或付费试点。

社区问题、功能建议和商业合作入口见 [SUPPORT.md](SUPPORT.md)。安全漏洞请按 [SECURITY.md](SECURITY.md) 私密报告。

## 首次发布的历史验证

V1.00.01 本地发布门禁已于 2026-09-11 通过：

- 前端 36 个模块构建成功，生产依赖漏洞 0；后端自动化测试 4/4。
- MySQL 8.4 全新数据卷成功执行 1 个 Flyway 迁移；首页/健康检查 200、匿名客户接口 401、管理员看板 200、AI 未配置 503。
- 原组织专属称谓、商业模块代码、高置信度凭据、环境/证书/数据文件和非测试手机号扫描均为 0。
- 前端 118 个锁定包、后端 83 个解析依赖及容器基础镜像完成许可证声明复核。
- 公开 Issue、商业合作 Issue 和私密漏洞报告入口已配置；用户已明确批准首次公开发布。

以上为V1.00.01的历史记录。当前验证及升级说明见[发布说明](RELEASE_NOTES.md)和[最新Release](https://github.com/LINLIN701/haiju-crm-community/releases/latest)。
