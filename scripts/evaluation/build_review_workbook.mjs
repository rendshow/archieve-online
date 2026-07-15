import fs from "node:fs/promises";
import path from "node:path";
import { SpreadsheetFile, Workbook } from "@oai/artifact-tool";


const args = parseArgs(process.argv.slice(2));
const inputDir = path.resolve(args.input ?? "evaluation/private/review-package");
const outputPath = path.resolve(args.output ?? path.join(inputDir, "Agent-V2-review.xlsx"));
const previewDir = path.resolve(args.previews ?? path.join(inputDir, "workbook-previews"));

const [summary, documents, pages, duplicates, facts, questions] = await Promise.all([
  readJson(path.join(inputDir, "package_summary.json")),
  readJsonl(path.join(inputDir, "documents.jsonl")),
  readJsonl(path.join(inputDir, "pages.jsonl")),
  readJsonl(path.join(inputDir, "duplicate_candidates.jsonl")),
  readJsonl(path.join(inputDir, "facts.jsonl")),
  readJsonl(path.join(inputDir, "question_candidates.jsonl")),
]);

const pageByKey = new Map(pages.map((row) => [row.pageKey, row]));
const workbook = Workbook.create();
const overview = workbook.worksheets.add("评测概览");
const documentSheet = workbook.worksheets.add("文档审阅");
const pageSheet = workbook.worksheets.add("页面与章节");
const duplicateSheet = workbook.worksheets.add("重复候选");
const factSheet = workbook.worksheets.add("事实标注");
const questionSheet = workbook.worksheets.add("问题候选");
const enumSheet = workbook.worksheets.add("枚举");

const documentRows = documents.map((row) => [
  row.documentKey,
  row.sourceGroup,
  row.proposedSplit,
  row.relativePath,
  row.fileName,
  row.fileNamePersonCandidate,
  row.reviewedPersonName,
  row.pageCount,
  row.expectedPageCountFromName ?? "",
  row.ocrReadyPages,
  row.fileSizeBytes,
  row.contactSheetPath,
  row.reviewStatus,
  row.reviewNotes,
]);

const pageRows = pages.map((row) => [
  row.pageKey,
  row.documentKey,
  row.pageNo,
  row.sectionCandidate,
  row.reviewedSection,
  row.qualityCandidate,
  row.ocrLineCount,
  row.ocrAverageConfidence ?? "",
  row.duplicateCandidateCount,
  row.ocrPreview,
  row.imagePath,
  row.reviewStatus,
  row.reviewNotes,
]);

const duplicateRows = duplicates.map((row) => [
  row.candidateKey,
  row.candidateType,
  row.scope,
  row.leftDocumentKey,
  row.leftPageNo,
  row.leftPageKey,
  pageByKey.get(row.leftPageKey)?.imagePath ?? "",
  row.rightDocumentKey,
  row.rightPageNo,
  row.rightPageKey,
  pageByKey.get(row.rightPageKey)?.imagePath ?? "",
  row.dHashDistance,
  row.ocrTextSimilarity,
  row.reviewedVerdict,
  row.reviewStatus,
  row.reviewNotes,
]);

const factRows = facts.map((row) => [
  row.factKey,
  row.documentKey,
  row.pageNo ?? "",
  row.fieldType,
  row.qualifier,
  row.candidateValue,
  row.reviewedValue,
  row.candidateSource,
  row.evidenceText,
  row.reviewStatus,
  row.reviewNotes,
]);

const questionRows = questions.map((row) => [
  row.questionKey,
  row.documentKey ?? row.expectedDocumentKeys?.[0] ?? "",
  row.capability,
  row.question,
  row.expectedAnswer,
  row.expectedEvidence,
  row.expectedOutcome,
  row.reviewStatus,
  row.reviewNotes,
]);

writeTableSheet(documentSheet, {
  tableName: "DocumentReviewTable",
  headers: ["文档键", "来源组", "建议分组", "相对路径", "文件名", "文件名姓名候选", "确认姓名", "页数", "文件名预期页数", "OCR完成页", "文件大小(B)", "缩略总览路径", "审核状态", "审核备注"],
  rows: documentRows,
  widths: [14, 24, 24, 48, 34, 18, 18, 10, 15, 12, 16, 56, 16, 34],
  rowHeight: 34,
});

writeTableSheet(pageSheet, {
  tableName: "PageReviewTable",
  headers: ["页面键", "文档键", "页码", "章节候选", "确认章节", "质量候选", "OCR行数", "OCR平均置信度", "重复候选数", "OCR摘要", "页面图片路径", "审核状态", "审核备注"],
  rows: pageRows,
  widths: [22, 14, 9, 28, 28, 18, 11, 16, 13, 70, 58, 16, 34],
  rowHeight: 48,
});

writeTableSheet(duplicateSheet, {
  tableName: "DuplicateReviewTable",
  headers: ["候选键", "候选类型", "范围", "左文档", "左页码", "左页面键", "左图片路径", "右文档", "右页码", "右页面键", "右图片路径", "dHash距离", "OCR相似度", "确认结论", "审核状态", "审核备注"],
  rows: duplicateRows,
  widths: [14, 24, 18, 14, 9, 22, 54, 14, 9, 22, 54, 12, 13, 22, 16, 34],
  rowHeight: 38,
});

writeTableSheet(factSheet, {
  tableName: "FactReviewTable",
  headers: ["事实键", "文档键", "页码", "字段类型", "限定项", "候选值", "确认值", "候选来源", "证据片段", "审核状态", "审核备注"],
  rows: factRows,
  widths: [20, 14, 9, 24, 20, 22, 22, 22, 70, 16, 34],
  rowHeight: 42,
});

writeTableSheet(questionSheet, {
  tableName: "QuestionReviewTable",
  headers: ["问题键", "文档键", "能力域", "用户问题", "标准答案", "标准证据", "预期结果状态", "审核状态", "审核备注"],
  rows: questionRows,
  widths: [20, 14, 28, 58, 54, 54, 24, 16, 34],
  rowHeight: 42,
});

const enumColumns = {
  "审核状态": ["PENDING", "CONFIRMED", "REJECTED", "UNREADABLE"],
  "数据分组": ["DEV", "ACCEPTANCE", "GENERALIZATION", "CHALLENGE_UNASSIGNED", "CHALLENGE_DEV", "CHALLENGE_ACCEPTANCE"],
  "章节类型": ["COVER", "STUDENT_STATUS", "TRANSCRIPT", "IDEOLOGICAL_ASSESSMENT", "EXPERT_REVIEW", "DEGREE_AWARD_DECISION", "GRADUATION_DEGREE_STATUS", "OTHER_GRADE_MATERIAL", "INVALID_PAGE", "DUPLICATE_PAGE", "OTHER"],
  "重复结论": ["EXACT_DUPLICATE", "NEAR_DUPLICATE", "NOT_DUPLICATE", "UNSURE"],
  "问题结果": ["ANSWERED", "NOT_FOUND", "INSUFFICIENT_EVIDENCE", "AMBIGUOUS", "INDEX_INCOMPLETE", "REFUSED"],
};
writeEnumSheet(enumSheet, enumColumns);

applyValidation(documentSheet, `C2:C${documents.length + 1}`, "枚举!$B$2:$B$7");
applyValidation(documentSheet, `M2:M${documents.length + 1}`, "枚举!$A$2:$A$5");
applyValidation(pageSheet, `E2:E${pages.length + 1}`, "枚举!$C$2:$C$12");
applyValidation(pageSheet, `L2:L${pages.length + 1}`, "枚举!$A$2:$A$5");
if (duplicates.length > 0) {
  applyValidation(duplicateSheet, `N2:N${duplicates.length + 1}`, "枚举!$D$2:$D$5");
  applyValidation(duplicateSheet, `O2:O${duplicates.length + 1}`, "枚举!$A$2:$A$5");
}
applyValidation(factSheet, `J2:J${facts.length + 1}`, "枚举!$A$2:$A$5");
applyValidation(questionSheet, `G2:G${questions.length + 1}`, "枚举!$E$2:$E$7");
applyValidation(questionSheet, `H2:H${questions.length + 1}`, "枚举!$A$2:$A$5");

buildOverview(overview, summary, documents.length, pages.length, duplicates.length, facts.length, questions.length);

await fs.mkdir(path.dirname(outputPath), { recursive: true });
await fs.mkdir(previewDir, { recursive: true });
const exported = await SpreadsheetFile.exportXlsx(workbook);
await exported.save(outputPath);

const previewRanges = {
  "评测概览": "A1:H25",
  "文档审阅": "A1:N12",
  "页面与章节": "A1:M12",
  "重复候选": "A1:P10",
  "事实标注": "A1:K12",
  "问题候选": "A1:I12",
  "枚举": "A1:E13",
};
for (const [name, range] of Object.entries(previewRanges)) {
  const preview = await workbook.render({ sheetName: name, range, scale: 1, format: "png" });
  await fs.writeFile(path.join(previewDir, `${name}.png`), new Uint8Array(await preview.arrayBuffer()));
}

const inspection = await workbook.inspect({
  kind: "workbook,sheet,table",
  maxChars: 12000,
  tableMaxRows: 4,
  tableMaxCols: 8,
  tableMaxCellChars: 80,
});
await fs.writeFile(path.join(previewDir, "inspection.ndjson"), inspection.ndjson ?? String(inspection), "utf8");
const formulaErrors = await workbook.inspect({
  kind: "match",
  searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
  options: { useRegex: true, matchCase: false, maxResults: 100 },
  maxChars: 8000,
});
const formulaErrorText = formulaErrors.ndjson ?? String(formulaErrors);
await fs.writeFile(path.join(previewDir, "formula-errors.ndjson"), formulaErrorText, "utf8");
if (!formulaErrorText.includes('"total":0') && !formulaErrorText.includes("matched 0")) {
  throw new Error(`formula error scan found suspicious cells: ${formulaErrorText}`);
}
console.log(JSON.stringify({ outputPath, previewDir, documentCount: documents.length, pageCount: pages.length }, null, 2));


function buildOverview(sheet, packageSummary, documentCount, pageCount, duplicateCount, factCount, questionCount) {
  sheet.showGridLines = false;
  sheet.getRange("A1:H2").merge();
  sheet.getRange("A1:H2").values = [["Agent V2 扫描档案评测审阅包"]];
  sheet.getRange("A1:H2").format = {
    fill: "#16324F",
    font: { bold: true, color: "#FFFFFF", size: 20 },
    verticalAlignment: "center",
    horizontalAlignment: "left",
  };
  sheet.getRange("A3:H3").merge();
  sheet.getRange("A3:H3").values = [["机器结果均为候选；只有审核状态为 CONFIRMED 的内容才能作为 Agent V2 评测事实。"]];
  sheet.getRange("A3:H3").format = { fill: "#DCEAF5", font: { color: "#16324F", bold: true }, wrapText: true };

  sheet.getRange("A5:B5").values = [["数据概况", "数量"]];
  sheet.getRange("A6:B10").values = [
    ["文档", documentCount],
    ["页面", pageCount],
    ["重复候选", duplicateCount],
    ["事实候选", factCount],
    ["问题候选", questionCount],
  ];
  sheet.getRange("D5:E5").values = [["审核进度", "待审核"]];
  sheet.getRange("D6:D10").values = [["文档"], ["页面"], ["重复候选"], ["事实"], ["问题"]];
  sheet.getRange("E6:E10").formulas = [
    [`=COUNTIF('文档审阅'!$M$2:$M$${documentCount + 1},"PENDING")`],
    [`=COUNTIF('页面与章节'!$L$2:$L$${pageCount + 1},"PENDING")`],
    [duplicateCount ? `=COUNTIF('重复候选'!$O$2:$O$${duplicateCount + 1},"PENDING")` : "=0"],
    [`=COUNTIF('事实标注'!$J$2:$J$${factCount + 1},"PENDING")`],
    [`=COUNTIF('问题候选'!$H$2:$H$${questionCount + 1},"PENDING")`],
  ];
  styleMiniTable(sheet.getRange("A5:B10"));
  styleMiniTable(sheet.getRange("D5:E10"));

  sheet.getRange("A12:H12").merge();
  sheet.getRange("A12:H12").values = [["建议审阅顺序"]];
  sheet.getRange("A12:H12").format = headerFormat("#2D6A4F");
  const reviewSteps = [
    "1. 先在“文档审阅”确认姓名和数据分组；边界组再划分为挑战开发集与挑战验收集。",
    "2. 在“页面与章节”确认材料章节、重复页、无效页和 OCR 质量。",
    "3. 在“事实标注”确认姓名、学号和日期；后续再追加课程成绩等任务字段。",
    "4. 在“重复候选”逐对核实，严格区分相同扫描页与同人不同材料。",
    "5. 最后完善“问题候选”的标准答案和页级证据，验收集在系统定版前保持不可见。",
  ];
  reviewSteps.forEach((step, index) => {
    const row = 13 + index;
    sheet.getRange(`A${row}:H${row}`).merge();
    sheet.getRange(`A${row}:H${row}`).values = [[step]];
  });
  sheet.getRange("A13:H17").format = { wrapText: true, verticalAlignment: "center", fill: "#F5F7FA" };

  sheet.getRange("A19:B19").values = [["生成参数", "值"]];
  sheet.getRange("A20:B25").values = [
    ["生成时间", packageSummary.generatedAt ? `生成于 ${packageSummary.generatedAt}` : ""],
    ["源目录", packageSummary.sourceRoot ?? ""],
    ["输出目录", packageSummary.outputRoot ?? ""],
    ["渲染 DPI", packageSummary.renderDpi ?? ""],
    ["OCR 已启用", packageSummary.ocrEnabled ?? false],
    ["清单版本", packageSummary.manifestVersion ?? ""],
  ];
  styleMiniTable(sheet.getRange("A19:B25"));
  sheet.getRange("A1:H25").format.rowHeight = 24;
  sheet.getRange("A1:H25").format.columnWidth = 18;
  sheet.getRange("A:A").format.columnWidth = 24;
  sheet.getRange("B:B").format.columnWidth = 44;
  sheet.freezePanes.freezeRows(3);
}

function writeTableSheet(sheet, { tableName, headers, rows, widths, rowHeight = 36 }) {
  sheet.showGridLines = false;
  const matrix = [headers, ...rows];
  const usedRows = Math.max(matrix.length, 2);
  const usedCols = headers.length;
  const range = sheet.getRangeByIndexes(0, 0, matrix.length, usedCols);
  range.values = matrix;
  range.format = { verticalAlignment: "top", wrapText: true };
  sheet.getRangeByIndexes(0, 0, 1, usedCols).format = headerFormat("#16324F");
  sheet.getRangeByIndexes(0, 0, 1, usedCols).format.rowHeight = 30;
  if (rows.length > 0) {
    sheet.getRangeByIndexes(1, 0, rows.length, usedCols).format.rowHeight = rowHeight;
  }
  sheet.freezePanes.freezeRows(1);
  sheet.freezePanes.freezeColumns(Math.min(2, usedCols));
  widths.forEach((width, index) => {
    sheet.getRangeByIndexes(0, index, usedRows, 1).format.columnWidth = width;
  });
  if (rows.length > 0) {
    const table = sheet.tables.add(`A1:${columnName(usedCols)}${matrix.length}`, true, tableName);
    table.style = "TableStyleMedium2";
    table.showBandedRows = true;
    table.showFilterButton = true;
  }
}

function writeEnumSheet(sheet, columns) {
  sheet.showGridLines = false;
  const entries = Object.entries(columns);
  const maxRows = Math.max(...entries.map(([, values]) => values.length));
  const matrix = [];
  matrix.push(entries.map(([name]) => name));
  for (let row = 0; row < maxRows; row += 1) {
    matrix.push(entries.map(([, values]) => values[row] ?? ""));
  }
  sheet.getRangeByIndexes(0, 0, matrix.length, entries.length).values = matrix;
  sheet.getRangeByIndexes(0, 0, 1, entries.length).format = headerFormat("#2D6A4F");
  sheet.getRangeByIndexes(0, 0, matrix.length, entries.length).format.columnWidth = 28;
  sheet.freezePanes.freezeRows(1);
}

function applyValidation(sheet, range, sourceRange) {
  sheet.getRange(range).dataValidation = { rule: { type: "list", formula1: sourceRange } };
}

function headerFormat(color) {
  return {
    fill: color,
    font: { bold: true, color: "#FFFFFF" },
    verticalAlignment: "center",
    horizontalAlignment: "left",
    wrapText: true,
  };
}

function styleMiniTable(range) {
  range.format = {
    borders: { preset: "all", style: "thin", color: "#CBD5E1" },
    verticalAlignment: "center",
  };
  range.getRow(0).format = headerFormat("#4B6078");
}

function columnName(count) {
  let value = count;
  let name = "";
  while (value > 0) {
    value -= 1;
    name = String.fromCharCode(65 + (value % 26)) + name;
    value = Math.floor(value / 26);
  }
  return name;
}

function parseArgs(values) {
  const parsed = {};
  for (let index = 0; index < values.length; index += 1) {
    const value = values[index];
    if (!value.startsWith("--")) continue;
    parsed[value.slice(2)] = values[index + 1];
    index += 1;
  }
  return parsed;
}

async function readJson(filePath) {
  return JSON.parse(await fs.readFile(filePath, "utf8"));
}

async function readJsonl(filePath) {
  const content = await fs.readFile(filePath, "utf8");
  return content.split(/\r?\n/).filter(Boolean).map((line) => JSON.parse(line));
}
