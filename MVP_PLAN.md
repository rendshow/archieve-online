# 在线档案馆 MVP 计划说明书

## 技术栈

- Java 21
- Spring Boot 3
- MySQL 8
- MyBatis-Plus
- Maven

## 第一阶段目标

先完成后端工程骨架，并跑通：

1. MySQL 连接。
2. MyBatis-Plus 配置。
3. 统一响应结构。
4. 5 个馆初始化。
5. 上传任务创建、查询、删除。

## 第二阶段目标

已接入原始文件上传：

1. 支持按任务上传多个文件。
2. 支持 PDF、图片、ZIP 类型识别。
3. 文件保存到本地 `storage/raw/{taskId}`。
4. 数据库记录原文件名、扩展名、大小、SHA-256、存储路径。

## MVP 主流程

选择馆 -> 创建上传任务 -> 上传文件 -> 工作区处理 -> AI mock 命名和标签 -> 人工审核 -> 正式入库 -> 查询、修改、删除。

## 模块规划

- `common`：通用响应、异常、配置。
- `hall`：馆管理。
- `task`：上传任务。
- `file`：原始文件上传与存储。
- `workspace`：工作区档案。
- `document`：正式档案。
- `tag`：标签。
- `ai`：AI 命名、AI 标签接口。
- `log`：操作日志。

## 后续迭代

1. 增加文件上传和本地存储。已完成。
2. 增加工作区文件与 mock AI 命名。已完成。
3. 增加审核入库。已完成。
4. 增加正式档案查询。已完成。
5. 增加软删除和操作日志。

## 第三阶段目标

已接入任务处理和工作区：

1. `POST /api/tasks/{taskId}/process` 会把上传文件转换成工作区文件。
2. mock AI 会生成建议名称、文件夹名称、摘要、命名理由。
3. mock AI 会生成基础标签，并写入 `tag` / `document_tag`。
4. 命名过程写入 `naming_log`，便于后续追溯和替换真实模型。

## 第四阶段目标

已接入审核入库和正式档案：

1. `POST /api/workspace-documents/{id}/approve` 会把工作区文件确认为正式档案。
2. 正式档案写入 `archive_document`。
3. 工作区标签会复制为正式档案标签。
4. 工作区文件状态更新为 `APPROVED`。
5. 任务状态更新为 `COMPLETED`。
6. 支持正式档案分页查询、详情、改名和删除。

## 第五阶段目标

已接入本地文档处理和格式转换：

1. PDF 输出 PDF：复制到工作区，后续走 AI 命名和审核。
2. PDF 输出 PNG：使用 PDFBox 将每页渲染为 PNG，生成一个或多个工作区档案。
3. 图片输出 PDF：使用 PDFBox 将图片写入 PDF。
4. 图片输出 PNG：统一转存为 PNG。
5. ZIP：解压其中的 PDF 和图片，并按任务输出格式处理。
6. 图片集 `ONE_TO_FIXED_N`：按固定数量分组生成多个 PDF。
7. `ONE_TO_ONE` 和 `ONE_TO_DYNAMIC_N`：当前 MVP 先按整组处理，后续接入智能拆分。

真实 OCR、视觉模型、夸克扫描增强暂未接入，但已经预留 `AiNamingService`、`AiTaggingService` 和文档处理服务接口。
