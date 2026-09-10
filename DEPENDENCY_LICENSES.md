# 第三方依赖与许可证复核

本清单是 V1.00.01 发布候选在 2026-09-11 的依赖快照，用于帮助发布者履行第三方许可证义务，不构成法律意见。精确版本以 `frontend/package-lock.json`、`backend/pom.xml` 和 Docker 构建时解析到的镜像摘要为准；升级依赖或基础镜像后必须重新复核。

## 前端锁文件

对 `frontend/package-lock.json` 的 118 个包条目进行了逐项许可证字段统计：

| SPDX/声明 | 数量 |
| --- | ---: |
| MIT | 99 |
| MPL-2.0 | 12 |
| Apache-2.0 | 2 |
| ISC | 2 |
| 0BSD | 1 |
| BSD-2-Clause | 1 |
| BSD-3-Clause | 1 |

未发现 `UNLICENSED`、缺失许可证字段或禁止再分发声明。`npm audit --omit=dev` 已在发布候选上返回 0 个已知漏洞；锁文件中的 `nanoid` 已升级到修复相关拒绝服务公告的 `3.3.19`。

## 后端依赖树

使用 `license-maven-plugin:2.5.0:add-third-party` 对 Maven 解析后的 83 个运行期与测试依赖生成清单，许可证族包括：

- Apache-2.0（含名称写法不同但文本相同的声明）；
- MIT、BSD-3-Clause；
- EPL-2.0、EPL-1.0 / MPL-2.0 双许可证；
- EDL-1.0；
- Logback 的 EPL-2.0 / LGPL 双许可证；
- Jakarta 注解 API 的 EPL-2.0 / GPL-2.0-with-Classpath-Exception 双许可证；
- MySQL Connector/J 的 GPL-2.0 with Universal FOSS Exception 1.0。

未发现专有、仅限非商业使用或禁止再分发依赖。MySQL Connector/J 的 Universal FOSS Exception 允许它与以完整对应源码发布的 OSI/FSF 自由软件协同分发，但 Connector/J 本身仍保持其原许可证；详情以 [Oracle 官方例外文本](https://oss.oracle.com/licenses/universal-foss-exception/) 为准。

复核命令：

```powershell
cd backend
.\mvnw.cmd --batch-mode org.codehaus.mojo:license-maven-plugin:2.5.0:add-third-party "-Dlicense.thirdPartyFilename=THIRD-PARTY.txt" "-Dlicense.failOnMissing=true"
```

生成的明细位于 `backend/target/generated-sources/license/THIRD-PARTY.txt`，属于可再生成的构建证据，不提交到仓库。

## 容器基础镜像

| 镜像 | 社区版用途 | 上游许可证与注意事项 |
| --- | --- | --- |
| `mysql:8.4` | 数据库运行时 | MySQL Community Server 为 GPL-2.0；镜像还包含操作系统组件，应保留镜像内许可证资料。参考 [MySQL Community Edition](https://www.mysql.com/products/community/) 与 [Docker Official Image 源码](https://github.com/docker-library/mysql)。 |
| `eclipse-temurin:17-jdk` / `17-jre` | Java 构建与运行时 | Eclipse Temurin 二进制按 GPL-2.0 with Classpath Exception 提供。参考 [Adoptium FAQ](https://adoptium.net/docs/faq)。 |
| `node:22-alpine` | 仅用于前端构建阶段 | Docker Node 镜像工程为 MIT；镜像内 Node.js、Alpine 和随附组件分别适用各自许可证。参考 [docker-node](https://github.com/nodejs/docker-node)。 |
| `nginx:1.30.4-alpine` | 静态前端与反向代理 | V1.00.01 使用 2026-09 发布时的官方稳定线；NGINX 使用 2-clause BSD，二进制再分发应保留版权、条件和免责声明；镜像内 Alpine 与其他组件另适用各自许可证。参考 [NGINX LICENSE](https://nginx.org/LICENSE) 与 [官方发布页](https://nginx.org/en/download.html)。 |

Dockerfile 使用多阶段构建，`node:22-alpine` 与 `eclipse-temurin:17-jdk` 不进入最终运行镜像；最终交付者仍应对实际发布的镜像运行 SBOM/许可证扫描并保留报告。

## 发布者义务摘要

- 本项目自身按 `AGPL-3.0-or-later` 发布，网络部署修改版时应向交互用户提供相应源码。
- 保留本仓库的 `LICENSE`、`NOTICE`、第三方版权及许可证说明。
- 修改 MPL/EPL 等文件级弱著佐权组件本身时，分别遵守其源码提供要求。
- 不把“妙海聚”或“海聚客户管理系统”的商标许可与源码许可混为一谈，品牌边界见 `TRADEMARKS.md`。
- 每次依赖升级后重新运行构建、漏洞审计、Maven 许可证清单和容器扫描。
