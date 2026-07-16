# Agent V2 私有评测数据

本目录只保存可复用工具和数据格式说明。真实扫描件、OCR 正文、页面预览、人工标注和审阅工作簿包含个人信息，必须写入 `evaluation/private/`，该目录已被 Git 忽略。

## 数据分层

- `DEV`：允许用于调试 OCR、检索、Planner 和证据门禁。
- `ACCEPTANCE`：与基线模板相同，但不得用于调参。
- `GENERALIZATION`：跨年份、学院和模板的冻结泛化组。
- `CHALLENGE_UNASSIGNED`：先完成人工异常分类，再拆分为边界调试与边界验收。

拆分单位始终是一整份人员档案 PDF，不得把同一人的页面放进不同集合。

## 生成私有评测包

生成器依赖当前 RapidOCR Python 环境、Pillow 和 Poppler。Windows 下建议显式传入 Poppler 可执行文件，避免 `.cmd` 包装器处理中文路径时出现问题。

```powershell
python scripts/evaluation/build_review_package.py `
  --source D:\CodeArea\Agent\testFile `
  --output evaluation/private/review-package `
  --pdfinfo C:\path\to\pdfinfo.exe `
  --pdftoppm C:\path\to\pdftoppm.exe
```

生成器支持断点续跑。已完成的页面渲染和 OCR JSON 会直接复用。

主要输出：

- `documents.jsonl`：文档清单和建议分组。
- `pages.jsonl`：页级 OCR、章节候选、图像指纹和预览路径。
- `facts.jsonl`：自动提取的待确认事实候选。
- `duplicate_candidates.jsonl`：页级重复候选。
- `question_candidates.jsonl`：待审阅问题候选。
- `package_summary.json`：评测包统计。
- `pages/`、`ocr/`、`contact-sheets/`：私有派生文件。

## 生成审阅工作簿

工作簿生成器使用 `@oai/artifact-tool`，将上述 JSONL 清单整理为带筛选、下拉审核项和进度公式的 Excel 文件：

```powershell
node scripts/evaluation/build_review_workbook.mjs `
  --input evaluation/private/review-package `
  --output evaluation/private/review-package/Agent-V2-review.xlsx
```

工作簿包括评测概览、文档审阅、页面与章节、重复候选、事实标注、问题候选和枚举七个页签。生成器还会渲染每个页签的代表性范围，并扫描常见公式错误。

## 迁移已审阅结果到 V2

V1 人工审阅完成后，使用迁移脚本生成多维标注工作簿。脚本不会覆盖原工作簿，并会校验每一页是否恰好归入一个材料实例：

```powershell
node scripts/evaluation/migrate_review_workbook_v2.mjs `
  --input evaluation/private/review-package/Agent-V2-review.xlsx `
  --package evaluation/private/review-package `
  --output evaluation/private/review-package/Agent-V2-review-v2.xlsx `
  --previews evaluation/private/review-package/workbook-v2-previews
```

V2 将原来的单一“章节分类”拆成四个互不替代的维度：

- `材料类型`：这是什么材料，例如成绩材料、专家评阅材料。
- `材料实例`：同类材料中的第几份，例如第 3 份专家评阅表。
- `页面角色`：该页在材料实例中的作用，例如封面、正文、续页。
- `治理状态`：该材料与标准结构的关系，例如缺失、替代、额外有效或疑似业务冗余。

`物理重复`只表示同一张原件被重复拍摄或扫描。不同材料实例使用相同模板、相同封面，不构成物理重复。额外的真实材料也不得自动视为重复或删除对象。

当前结构规则覆盖五类样本：1988 中专两页结构、1999 本科四页结构、混杂泛化两页结构、博士学位档案结构和硕士学位档案结构。规则测试命令：

```powershell
node --test scripts/evaluation/test_review_structure_rules.mjs
```

## 审阅原则

1. 文件名只能作为元数据候选，不能直接作为正文事实。
2. 自动章节、姓名、学号、日期和重复判断全部需要人工确认。
3. 看不清的内容标记为 `UNREADABLE`，不得猜测。
4. 原 PDF 是最终凭据，所有标注必须能回到文档和页码。
5. 混杂泛化组和冻结验收组不得因为 Agent 回答错误而修改事实；只有发现人工标注错误时才能修订并记录原因。
6. 标准结构用于发现偏离，不用于覆盖人工事实；治理结论不得触发删除、合并或其他写操作。
