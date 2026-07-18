# OpenSearch 基础设施

OpenSearch 只保存可重建的页级检索索引。正式档案原件仍在 MinIO，元数据、字段事实和索引任务状态仍在 MySQL。

## 启动前检查

在 Linux 的基础设施目录执行：

```bash
mkdir -p data/opensearch
sudo chown -R 1000:1000 data/opensearch
sudo sysctl -w vm.max_map_count=262144
umask 077
printf 'OPENSEARCH_INITIAL_ADMIN_PASSWORD=%s\n' '替换为至少 8 位的强密码' > .env
docker compose -f docker-compose.infra.yml up -d opensearch
curl http://127.0.0.1:9200
```

即使开发环境配置了 `plugins.security.disabled=true`，OpenSearch 2.12+ 的容器初始化仍要求该密码。`.env` 仅保留在远端基础设施目录，不提交 Git，也不写入 Spring 配置。

要永久保存内核参数，将 `vm.max_map_count=262144` 写入 `/etc/sysctl.conf` 或 `/etc/sysctl.d/` 中的独立配置文件。

## 国内镜像与镜像覆盖

Compose 不写死任何第三方镜像地址。优先在 Docker daemon 配置可信镜像加速器；若当前环境已有可用的 OpenSearch 镜像代理，可临时覆盖镜像：

```bash
OPENSEARCH_IMAGE=你的镜像地址 docker compose -f docker-compose.infra.yml up -d opensearch
```

服务数据固定存放在 `./data/opensearch`，对应当前远程基础设施目录下的 `/mnt/Data/Docker/danganguan-infra/data/opensearch`。不要使用 `docker compose down -v`，它会删除命名卷；本项目的 OpenSearch 使用的是目录挂载。

## 应用配置

启用应用侧同步前，在 `application-local.yml` 或环境变量中设置：

```yaml
archive:
  search:
    opensearch:
      enabled: true
      endpoint: http://100.109.69.52:9200
      index-alias: archive-page-read
```

当前 compose 为开发环境单节点配置，关闭了 OpenSearch 内建安全插件。仅应通过受信任的内网或 Tailscale 使用，生产部署必须启用认证和 TLS。
