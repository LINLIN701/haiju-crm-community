# 海聚客户管理系统社区版

海聚客户管理系统社区版是妙海聚面向零售门店提供的自托管客户经营基础版，聚焦客户档案、基础跟进、消费记录、导入导出和人工确认式 AI 文字录入。

[![CI](https://github.com/LINLIN701/haiju-crm-community/actions/workflows/ci.yml/badge.svg)](https://github.com/LINLIN701/haiju-crm-community/actions/workflows/ci.yml)

**第一次使用：** [部署教程](docs/DEPLOYMENT.md) · [带截图的使用教程](docs/USER_GUIDE.md) · [练习CSV](docs/examples/customers-sample.csv)

当前版本 **V1.00.02**。本次补齐教程、真实截图和版本标识，业务功能与V1.00.01相同。

> 社区源码已于 2026-09-11 公开到 `LINLIN701/haiju-crm-community`，包含独立部署所需的前端、后端、数据库迁移和 Docker Compose 配置。版本与下载以 [Releases](https://github.com/LINLIN701/haiju-crm-community/releases) 为准。

## 社区版适合谁

- 希望自行部署和掌控数据的单店或小团队。
- 已有 Excel 客户资料，希望建立持续跟进记录的零售门店。
- 愿意自备外部模型 Key，并接受 AI 结果人工确认的团队。
- 开发者和实施伙伴，用于评估海聚的客户经营基础能力。

## 社区版能力

- 客户档案、标签、搜索和去重基础能力。
- 客户导入、导出和基础消费记录。
- 跟进记录、触达计划和通知。
- 手动 AI 文字录入，用户自备模型 Key。
- 基础权限、操作日志和独立数据库部署。

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

## 当前发布状态

V1.00.01 本地发布门禁已于 2026-09-11 通过：

- 前端 36 个模块构建成功，生产依赖漏洞 0；后端自动化测试 4/4。
- MySQL 8.4 全新数据卷成功执行 1 个 Flyway 迁移；首页/健康检查 200、匿名客户接口 401、管理员看板 200、AI 未配置 503。
- 原组织专属称谓、商业模块代码、高置信度凭据、环境/证书/数据文件和非测试手机号扫描均为 0。
- 前端 118 个锁定包、后端 83 个解析依赖及容器基础镜像完成许可证声明复核。
- 公开 Issue、商业合作 Issue 和私密漏洞报告入口已配置；用户已明确批准首次公开发布。

最终公开状态以 [GitHub 仓库](https://github.com/LINLIN701/haiju-crm-community)和 `V1.00.01` Release 为准。
