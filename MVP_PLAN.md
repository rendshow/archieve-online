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

## 第六阶段目标

已重构上传输入组和按人归档策略：

1. 任务策略从文件数量映射调整为按人归档策略。
2. `SINGLE_PERSON`：一个输入组只包含一个人的材料，输出一个档案文件。
3. `FIXED_ELEMENTS_PER_PERSON`：每 N 个连续输入元素属于一个人，输出多个档案文件。
4. `AI_PERSON_BOUNDARY`：预留给 AI 判断人员边界，当前 MVP 暂按整组处理。
5. 每次上传都会生成新的输入组，不会和上一次上传混在一起。
6. ZIP、PDF 等单文件各自独立成组。
7. 同一次上传的散图合并为一个 `LOOSE_IMAGES` 输入组。

已有数据库需要手动执行：

```text
docs/sql/20260702-upload-group-columns.sql
```

## 第七阶段目标

已接入文档分析层和可配置 AI Provider：

1. 新增 `DocumentAnalyzeService`，统一输出识别文本、摘要、疑似姓名、关键词、置信度和分析理由。
2. 默认 `local` 实现：PDF 先抽文本层，图片/扫描件暂按文件名和上下文做基础分析。
3. 新增 `openai-compatible` 实现：可调用兼容 Chat Completions 的外部模型，并支持 PNG 视觉输入。
4. AI 命名和 AI 标签开始使用文档分析结果，不再只依赖原始文件名。
5. 工作区档案新增 `ocr_text`，审核归档时同步到正式档案。

已有数据库需要手动执行：

```text
docs/sql/20260702-workspace-document-analysis.sql
```

切换外部模型需要设置环境变量：

```text
ARCHIVE_AI_PROVIDER=openai-compatible
ARCHIVE_AI_BASE_URL=兼容接口地址
ARCHIVE_AI_API_KEY=你的密钥
ARCHIVE_AI_MODEL=模型名
```
