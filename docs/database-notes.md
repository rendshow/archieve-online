# 数据库开发说明

## 1. 当前初始化方式

当前 MVP 使用 Spring Boot SQL 初始化：

- `src/main/resources/db/schema.sql`
- `src/main/resources/db/data.sql`

配置位置：

- `src/main/resources/application.yml`

数据库连接串已带上：

```text
createDatabaseIfNotExist=true
```

开发期如果 MySQL 用户有建库权限，应用启动时会自动创建 `danganguan_online` 数据库。

## 2. 为什么数据库看起来没变化

`schema.sql` 里使用的是：

```sql
CREATE TABLE IF NOT EXISTS ...
```

这只会在表不存在时创建表，不会自动修改已经存在的表结构。

因此如果你在表已经创建之后修改了 `schema.sql`：

- 新增表会创建。
- 已存在表不会自动加字段。
- 已存在字段类型不会自动变更。
- 已存在索引不会自动调整。

开发阶段如果需要让结构完全跟随 `schema.sql`，可以手动删除本地库后重启应用。

后续正式做法建议接入：

- Flyway
- 或 Liquibase

## 3. 删除策略

当前系统采用软删除。

删除任务时不会物理删除数据库记录，而是：

- `archive_task.deleted = 1`
- 任务下的 `uploaded_file.deleted = 1`
- 任务下的 `workspace_document.deleted = 1`

同时状态会改为：

- `uploaded_file.status = DELETED`
- `workspace_document.status = DELETED`

MyBatis-Plus 会自动在普通查询中追加逻辑删除条件，因此接口查询不到这些记录，但你在数据库表里仍然能看到行。

## 4. MyBatis-Plus 分页插件

MyBatis-Plus 3.5.9 的分页插件依赖需要显式引入：

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-jsqlparser</artifactId>
    <version>${mybatis-plus.version}</version>
</dependency>
```

当前项目已经引入该依赖，`mvn test` 可正常编译。

如果 IDE 中 `PaginationInnerInterceptor` 仍然标红，通常是 Maven 依赖没有刷新。
