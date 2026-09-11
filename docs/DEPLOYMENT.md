# 部署教程

适用版本：社区版 V1.00.02；业务操作也适用于 V1.00.01。本文使用 Docker Compose 部署前端、后端和独立 MySQL 8.4。只运行 Docker 版时，不需要在宿主机另外安装 Java、Node.js 或 MySQL。

[返回首页](../README.md) · [使用教程](USER_GUIDE.md) · [常见问题](#常见问题)

## 1. 安装前准备

准备一台能够访问 GitHub、Docker 镜像仓库、Maven 和 npm 的电脑或服务器。首次构建需要联网；后续使用普通客户功能不依赖外部模型。

- **Windows 电脑**：按 [Docker Desktop 官方安装说明](https://docs.docker.com/desktop/setup/install/windows-install/)安装并启动 Docker Desktop，使用 Linux containers。安装界面要求的 WSL/虚拟化设置以官方说明为准。下面 Windows 命令使用 PowerShell 7。
- **Ubuntu 服务器**：按 [Docker Engine 官方 Ubuntu 安装说明](https://docs.docker.com/engine/install/ubuntu/)安装 Engine 和 Compose 插件，确保当前账号能运行 Docker。以下命令假定已具有 Docker 权限。
- **源码下载**：安装 Git；不使用 Git 时，也可以在 [Release 页面](https://github.com/LINLIN701/haiju-crm-community/releases)下载 Source code ZIP 并解压。

先检查：

```console
docker version
docker compose version
docker info
```

`docker version` 应同时显示 Client 和 Server。只有 Client 或出现连接 daemon 失败时，先启动 Docker 引擎。教程演练使用 Windows + Docker Desktop + Linux 容器；不把示例机器的可运行结果当作所有硬件的容量承诺。

## 2. 下载指定版本

```console
git clone --branch V1.00.02 --depth 1 https://github.com/LINLIN701/haiju-crm-community.git
cd haiju-crm-community
```

解压 ZIP 的用户进入含 `docker-compose.yml` 的那一层目录。后续命令都在这个目录执行。

**本文固定使用 Compose 项目名 `haiju`，用于全新安装。已有部署必须沿用原项目名**，先用 `docker compose ls` 查看。更改项目名会指向另一套数据卷，看起来像数据丢失；不能通过删卷解决。

## 3. 创建配置

Windows / PowerShell：

```powershell
Copy-Item .env.example .env
notepad .env
```

Ubuntu / Bash：

```bash
cp .env.example .env
chmod 600 .env
nano .env
```

| 配置项 | 填写内容 |
| --- | --- |
| `MYSQL_DATABASE` | 全新社区数据库名，例如 `haiju_community` |
| `MYSQL_USER` | 独立数据库账号，例如 `haiju` |
| `MYSQL_PASSWORD` | 数据库用户随机强密码 |
| `MYSQL_ROOT_PASSWORD` | 另一份独立随机强密码，不与管理员密码共用 |
| `APP_ADMIN_USERNAME` | 登录账号，默认名称为 `admin`，可修改 |
| `APP_ADMIN_PASSWORD` | 至少12位；推荐密码管理器生成24位以上随机密码 |
| `APP_CORS_ALLOWED_ORIGIN` | 本机为 `http://localhost:8088`；公网为实际HTTPS站点，例如 `https://crm.example.com`，末尾不要加 `/` |
| `APP_AI_BASE_URL`、`APP_AI_API_KEY`、`APP_AI_MODEL` | 初次部署可全部留空；配置方法见下文 |

替换 `.env.example` 中所有“请替换”提示文字，它们不是可共用的初始密码。为了减少手工配置错误，可使用长随机字母数字密码。密码含 `$`、`#` 或空格时，要正确处理 `.env` 引号与插值，参见 [Compose 环境变量规则](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/)。不要把 `.env`、密码或 `docker compose config` 的完整输出发到公开 Issue。

数据库只用于这份社区部署，不应指向已有业务库。MySQL 容器不对宿主机开放3306端口，因此通常不与宿主机已有MySQL端口冲突。

## 4. 启动并检查

```console
docker compose -p haiju config --quiet
docker compose -p haiju up -d --build
docker compose -p haiju ps
```

首次构建可能需要数分钟。MySQL显示 `healthy`、backend和frontend显示运行中后，打开：

- 登录：`http://localhost:8088/login`
- 健康检查：`http://localhost:8088/api/v1/health`（应返回HTTP200）

使用 `.env` 中的管理员账号和密码登录。空库初次启动由Flyway自动建表，不需要导入原系统SQL或手工创建业务表。

若暂时出现502，稍候刷新并查看：

```console
docker compose -p haiju logs --tail 100 backend
docker compose -p haiju logs --tail 100 mysql
```

默认映射 `8088:80` 可能监听所有网络接口。本机练习建议把 `docker-compose.yml` 中该行改为 `127.0.0.1:8088:80`，然后重新执行启动命令。普通HTTP仅用于可信本机访问；公网使用下面的HTTPS方案。

## 5. 公网HTTPS部署

前提：你控制的域名已解析到服务器，公网80/443可达，服务器已有证书自动管理工具。本例使用**安装在同一宿主机上的 Caddy**；不是另一个容器中的 `127.0.0.1`。

1. 在 `docker-compose.yml` 把前端端口改为 `127.0.0.1:8088:80`，只允许宿主机反向代理访问。
2. 在 `.env` 设置 `APP_CORS_ALLOWED_ORIGIN=https://你的实际域名`。
3. 按 [Caddy 官方安装说明](https://caddyserver.com/docs/install)安装后，使用下列 Caddyfile；将示例域名替换为自己的域名。

```caddyfile
crm.example.com {
    reverse_proxy 127.0.0.1:8088
}
```

4. 执行 `docker compose -p haiju up -d`。若在Ubuntu按官方软件包安装Caddy，将配置写入 `/etc/caddy/Caddyfile` 后，执行下面两条命令验证并重载（第一条成功后才执行第二条）：

```bash
sudo caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
sudo systemctl reload caddy
```

5. 访问 `https://你的实际域名/login`，核对浏览器证书有效，再登录。不要开放8088绕过HTTPS，也不需要对公网开放3306或后端8080。

域名证书签发与续期条件见 [Caddy Automatic HTTPS](https://caddyserver.com/docs/automatic-https)。本教程核对了配置，但没有为读者的公网域名实际签发证书。HTTP Basic会随请求发送凭据，公网部署必须保护整个站点的传输链路。

## 6. 可选：连接外部AI模型

在部署机器的 `.env` 中填写三项：

```dotenv
APP_AI_BASE_URL=https://你的模型服务域名/v1
APP_AI_API_KEY=在本机填写自己的密钥
APP_AI_MODEL=服务商提供的确切模型标识
```

以上为占位说明，不是可直接使用的服务。Base URL应是 **`/chat/completions` 之前的地址**；系统会自行追加 `/chat/completions`。例如供应商完整接口是 `https://example.com/v1/chat/completions`，这里填 `https://example.com/v1`。模型必须支持该兼容接口及结构化文本回复。

修改后重建后端容器使环境变量生效：

```console
docker compose -p haiju up -d --force-recreate backend
```

仅执行 `restart` 不会应用新环境变量。进入“AI 资料录入”，先用一段虚构资料试解析，核对结果后再保存。发给AI的文字会发送到所配置的服务商，请自行确认数据处理范围和费用。密钥只配置在服务器，不填在网页、文档或Issue里。

未配置时，AI页面会显示明确错误，普通客户功能仍可使用。本次教程未配置真实付费模型，截图展示真实的“外部模型未配置”状态。

## 7. 停止、重启与修改密码

| 目的 | 命令 |
| --- | --- |
| 暂停服务 | `docker compose -p haiju stop` |
| 恢复已创建容器 | `docker compose -p haiju start` |
| 仅重启服务 | `docker compose -p haiju restart` |
| 应用配置或源码变化 | `docker compose -p haiju up -d --build` |
| 移除容器但保留数据库卷 | `docker compose -p haiju down` |

**日常停止和升级不要使用 `down -v`，也不要删除 `haiju_mysql_data` 对应的数据卷。** 数据不在前端页面或容器可写层，而在Compose命名卷中。

修改管理员密码：修改 `.env` 的 `APP_ADMIN_PASSWORD`，执行 `docker compose -p haiju up -d --force-recreate backend`，退出网页后用新密码登录。现有MySQL数据卷的数据库账号密码不会因修改 `.env` 自动轮换；已有数据库密码变更需要另行协调数据库账号和应用配置，不能只改文件。

## 8. 数据库备份

客户CSV不是完整备份：它不含跟进、消费、日志，且当前最多导出最近500位客户。日常备份应保存数据库SQL和单独受保护的 `.env`，并复制到另一台受控设备。公开仓库不保存这两类文件。

创建备份目录（Windows）：

```powershell
New-Item -ItemType Directory -Force backups
```

Ubuntu：

```bash
mkdir -p backups
chmod 700 backups
```

安排短暂维护窗口，暂停应用写入，然后在**容器内部**生成UTF-8 SQL文件。以下命令在PowerShell 7和Bash中均可使用，不会将数据库密码展开到宿主机命令文本：

```console
docker compose -p haiju stop backend
docker compose -p haiju exec -T mysql sh -c 'umask 077; MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --quick --no-tablespaces --set-gtid-purged=OFF --default-character-set=utf8mb4 "$MYSQL_DATABASE" > /tmp/haiju-backup.sql'
docker compose -p haiju cp mysql:/tmp/haiju-backup.sql ./backups/haiju-backup-2026-09-11.sql
docker compose -p haiju exec -T mysql rm -f /tmp/haiju-backup.sql
docker compose -p haiju start backend
```

将示例日期换成实际日期和时间，每次使用不同文件名，避免覆盖旧备份。**每一步成功后再执行下一步**；导出或复制失败时保留错误信息，恢复backend服务后排查，不清理唯一的备份文件。这里的 `rm` 仅移除刚生成的容器内临时SQL，不删除数据库或已复制的备份。

检查文件非空并记录SHA256：PowerShell使用 `Get-FileHash ./backups/文件名.sql -Algorithm SHA256`；Linux使用 `sha256sum ./backups/文件名.sql`。真正可用的备份还需要恢复演练。参数含义可查 [MySQL 8.4 mysqldump官方文档](https://dev.mysql.com/doc/refman/8.4/en/mysqldump.html)。

## 9. 在新空库验证恢复

下面只创建独立项目 `haiju-restore` 的MySQL，不启动其前端，不覆盖正在营业的 `haiju` 数据卷。执行前用 `docker compose ls` 确认这个名字没有被已有系统使用；若已使用，换一个全新项目名并在本节所有命令中一致替换。

```console
docker compose -p haiju-restore up -d mysql
docker compose -p haiju-restore ps
```

等待MySQL健康，再将选定备份放入该新容器：

```console
docker compose -p haiju-restore cp ./backups/haiju-backup-2026-09-11.sql mysql:/tmp/haiju-restore.sql
docker compose -p haiju-restore exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --default-character-set=utf8mb4 "$MYSQL_DATABASE" < /tmp/haiju-restore.sql'
docker compose -p haiju-restore exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE" -e "SELECT COUNT(*) AS customers FROM customers; SELECT COUNT(*) AS contacts FROM contact_records; SELECT COUNT(*) AS consumptions FROM consumptions;"'
```

备份包含重建表语句，**不得将恢复命令直接改成正在使用的生产项目名**。核对客户、跟进、消费和日志数量及抽样内容后，才确认备份可恢复。正式灾难恢复需要停写、保留事故现场和当前备份，在新环境恢复并验收后切换访问入口；不要把回退源码等同于回退数据库。

演练完成后，确认项目名确实是本次临时项目，再清理：

```console
docker compose -p haiju-restore down -v
```

这条命令会永久删除**演练项目**的数据卷，所以不用于常规停机。本次教程已用纯虚构数据验证备份及独立空库恢复，逐表计数一致。

## 10. 升级与回退

升级前备份数据库、保存当前 `.env` 和部署中修改过的端口配置，记录当前Git提交与Compose项目名，阅读目标版本发布说明。先在独立环境验证新版本。

Git安装可以获取标签后切换指定版本，例如从V1.00.01升级到本教程版本：

```console
git fetch origin --tags
git status --short
git switch --detach V1.00.02
docker compose -p haiju up -d --build
docker compose -p haiju ps
```

遇到本地文件冲突先保留自己的配置再处理，不使用强制重置。ZIP安装建议解压到新目录、复制受保护的配置，并沿用原Compose项目名；别让目录名变化创建一套空数据卷。

V1.00.02只新增教程、截图和版本标识，没有新数据库迁移；可切回V1.00.01标签并重建应用进行代码回退。未来版本若含迁移，不能默认旧代码兼容新库，应按对应版本方案恢复到新环境。升级后验证登录、客户详情、跟进、消费、概览和日志，不只检查首页是否打开。

## 常见问题

| 现象 | 检查与处理 |
| --- | --- |
| Docker连接失败 | 启动Docker Desktop/Engine，确认Linux容器模式及 `docker info` 可用 |
| 拉取镜像/Maven/npm超时 | 检查部署机到对应官方仓库的网络；保存具体报错后重试构建 |
| 8088已占用 | 改映射左侧端口，如 `127.0.0.1:18088:80`；同步Origin和访问地址，再执行 `up -d` |
| 登录401 | 核对 `.env` 管理员账号、密码及是否重建了backend；退出后重新登录 |
| 启动提示至少12位密码 | 替换管理员密码，不使用示例占位文字 |
| 首页502/后端退出 | 检查MySQL健康、backend日志和数据库凭据；不要删卷碰运气 |
| 修改数据库密码后无法连接 | 已有MySQL卷不会重新执行初始化密码设置；先恢复原配置或按数据库密码轮换流程处理 |
| 看起来没有数据 | 检查Compose项目名、数据库名和实际数据卷，避免启动了另一套空环境 |
| AI返回503 | 三项模型配置不完整；普通CRM操作仍可使用 |
| AI返回502 | 检查Base URL、模型名称、额度/网络和服务商返回；携带页面显示的调用日志编号排查 |
| 导入大CSV失败 | 当前Nginx[默认请求体上限](https://nginx.org/en/docs/http/ngx_http_core_module.html#client_max_body_size)可能先在约1MiB处拦截，后端文件上限2MB；先拆成小于1MiB且不超过5000行的UTF-8 CSV |

求助时使用 [GitHub Issue](https://github.com/LINLIN701/haiju-crm-community/issues/new/choose)，只附脱敏日志、版本和操作步骤；漏洞使用 [私密漏洞报告](https://github.com/LINLIN701/haiju-crm-community/security/advisories/new)。
