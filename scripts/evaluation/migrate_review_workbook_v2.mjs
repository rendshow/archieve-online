import fs from "node:fs/promises";
import path from "node:path";
import { FileBlob, SpreadsheetFile, Workbook } from "@oai/artifact-tool";

import { buildDocumentStructure, TEMPLATE_DEFINITIONS } from "./review_structure_rules.mjs";


const args = parseArgs(process.argv.slice(2));
const inputPath = path.resolve(args.input ?? "evaluation/private/review-package/Agent-V2-review.xlsx");
const packageDir = path.resolve(args.package ?? "evaluation/private/review-package");
const outputPath = path.resolve(args.output ?? path.join(packageDir, "Agent-V2-review-v2.xlsx"));
const previewDir = path.resolve(args.previews ?? path.join(packageDir, "workbook-v2-previews"));

if (inputPath === outputPath) {
  throw new Error("V2 output must not overwrite the reviewed V1 workbook");
}

const sourceWorkbook = await SpreadsheetFile.importXlsx(await FileBlob.load(inputPath));
const [documents, pages] = await Promise.all([
  readJsonl(path.join(packageDir, "documents.jsonl")),
  readJsonl(path.join(packageDir, "pages.jsonl")),
]);

const reviewedDocuments = rowsFromSheet(sourceWorkbook, "文档审阅");
const reviewedPages = rowsFromSheet(sourceWorkbook, "页面与章节");
const reviewedDuplicates = rowsFromSheet(sourceWorkbook, "重复候选");
const factValues = sourceWorkbook.worksheets.getItem("事实标注").getUsedRange(true).values;
const questionValues = sourceWorkbook.worksheets.getItem("问题候选").getUsedRange(true).values;

const reviewedDocumentByKey = new Map(reviewedDocuments.map((row) => [String(row["文档键"]), row]));
const reviewedPageByKey = new Map(reviewedPages.map((row) => [String(row["页面键"]), row]));
const pageByKey = new Map(pages.map((row) => [row.pageKey, row]));

const documentRows = [];
const materialRows = [];
const pageRows = [];
const governanceRows = [];
const materialByPageKey = new Map();

for (const document of documents) {
  const reviewedDocument = reviewedDocumentByKey.get(document.documentKey) ?? {};
  const structure = buildDocumentStructure(document);
  const legibility = String(reviewedDocument["审核状态"] ?? "") === "UNREADABLE" ? "UNREADABLE" : "READABLE";
  const documentReviewStatus = String(reviewedDocument["审核状态"] ?? "").trim() ? "COMPLETED" : "PENDING";
  const humanNotes = String(reviewedDocument["审核备注"] ?? "");

  documentRows.push([
    document.documentKey,
    document.sourceGroup,
    document.fileName,
    structure.template.name,
    structure.template.expectedPageCount,
    document.pageCount,
    structure.pageDelta,
    structure.template.composition,
    structure.structureAssessment,
    legibility,
    documentReviewStatus,
    humanNotes,
    document.contactSheetPath,
    reviewedDocument["建议分组"] ?? document.proposedSplit,
  ]);

  if (legibility === "UNREADABLE") {
    governanceRows.push(governanceRow(
      governanceRows.length + 1,
      "DOCUMENT",
      document.documentKey,
      document.documentKey,
      "UNREADABLE_IDENTITY",
      "文档身份信息存在不可辨认内容",
      "",
      "保留原件并进行二次人工核验，不得根据文件名补全",
      "NEEDS_FOLLOWUP",
    ));
  }

  for (const material of structure.instances) {
    const pageList = material.pages.map((item) => item.pageNo).join(",");
    const actualPageCount = material.pages.length;
    const pageCountAssessment = actualPageCount === 0
      ? "MISSING"
      : actualPageCount === material.expectedPageCount
        ? "MATCH"
        : actualPageCount > material.expectedPageCount
          ? "EXTRA_PAGES"
          : "FEWER_PAGES";
    const needsReview = material.governanceStatus !== "EXPECTED";
    materialRows.push([
      material.instanceKey,
      document.documentKey,
      material.materialType,
      material.materialSubtype,
      material.instanceNo,
      pageList,
      actualPageCount,
      material.expectedPageCount,
      pageCountAssessment,
      material.governanceStatus,
      "",
      needsReview ? "PENDING" : "MIGRATED_CONFIRMED",
      needsReview ? humanNotes : "",
    ]);

    for (let index = 0; index < material.pages.length; index += 1) {
      const materialPage = material.pages[index];
      const pageKey = `${document.documentKey}-P${String(materialPage.pageNo).padStart(4, "0")}`;
      materialByPageKey.set(pageKey, { material, materialPage, pageNoInMaterial: index + 1 });
    }

    if (needsReview) {
      governanceRows.push(governanceRowForMaterial(governanceRows.length + 1, document, material, humanNotes));
    }
  }
}

for (const pageRecord of pages) {
  const reviewedPage = reviewedPageByKey.get(pageRecord.pageKey) ?? {};
  const mapping = materialByPageKey.get(pageRecord.pageKey);
  if (!mapping) {
    throw new Error(`page has no V2 material instance: ${pageRecord.pageKey}`);
  }
  const effectiveV1Section = String(reviewedPage["确认章节"] ?? "").trim()
    || String(reviewedPage["章节候选"] ?? pageRecord.sectionCandidate).trim();
  const recheckRequired = mapping.material.governanceStatus !== "EXPECTED";
  pageRows.push([
    pageRecord.pageKey,
    pageRecord.documentKey,
    pageRecord.pageNo,
    pageRecord.sectionCandidate,
    effectiveV1Section,
    mapping.material.instanceKey,
    mapping.material.materialType,
    mapping.material.materialSubtype,
    mapping.materialPage.role,
    mapping.material.instanceNo,
    mapping.pageNoInMaterial,
    mapping.material.governanceStatus,
    "HUMAN_V1_AND_STRUCTURE_RULE",
    recheckRequired,
    reviewedPage["审核备注"] ?? "",
    pageRecord.qualityCandidate,
    pageRecord.imagePath,
    pageRecord.ocrPreview,
  ]);
}

const duplicateRows = reviewedDuplicates.map((row) => [
  row["候选键"],
  row["候选类型"],
  row["左文档"],
  row["左页码"],
  row["左页面键"],
  row["右文档"],
  row["右页码"],
  row["右页面键"],
  row["审核状态"] === "REJECTED" ? "NOT_DUPLICATE" : row["确认结论"],
  row["审核状态"] === "REJECTED" ? "COMPLETED" : "PENDING",
  "同模板页面不等于同一原件重复扫描",
]);

const workbook = Workbook.create();
const overview = workbook.worksheets.add("V2概览");
const documentSheet = workbook.worksheets.add("文档结构");
const materialSheet = workbook.worksheets.add("材料实例");
const pageSheet = workbook.worksheets.add("页面结构");
const governanceSheet = workbook.worksheets.add("治理复核");
const duplicateSheet = workbook.worksheets.add("物理重复复核");
const factSheet = workbook.worksheets.add("事实标注");
const questionSheet = workbook.worksheets.add("问题候选");
const enumSheet = workbook.worksheets.add("枚举V2");

writeTableSheet(documentSheet, {
  tableName: "DocumentStructureV2Table",
  headers: ["文档键", "来源组", "文件名", "标准模板", "标准页数", "实际页数", "页数差", "标准组成", "结构判断", "可读性", "审核状态", "原人工备注", "文档总览路径", "数据分组"],
  rows: documentRows,
  widths: [14, 24, 34, 28, 11, 11, 10, 58, 20, 15, 16, 58, 58, 24],
  rowHeight: 42,
});

writeTableSheet(materialSheet, {
  tableName: "MaterialInstanceV2Table",
  headers: ["材料实例键", "文档键", "材料类型", "材料子类型", "实例序号", "包含页码", "实际页数", "标准页数", "页数判断", "迁移治理判断", "人工治理结论", "审核状态", "审核备注"],
  rows: materialRows,
  widths: [22, 14, 28, 36, 11, 18, 11, 11, 18, 24, 24, 20, 58],
  rowHeight: 38,
});

writeTableSheet(pageSheet, {
  tableName: "PageStructureV2Table",
  headers: ["页面键", "文档键", "页码", "机器旧候选", "V1人工有效分类", "材料实例键", "材料类型", "材料子类型", "页面角色", "实例序号", "材料内页码", "治理状态", "迁移依据", "需要复核", "人工原备注", "OCR质量", "页面图片路径", "OCR摘要"],
  rows: pageRows,
  widths: [22, 14, 9, 26, 28, 22, 28, 36, 20, 11, 13, 24, 32, 12, 58, 18, 58, 70],
  rowHeight: 48,
});

writeTableSheet(governanceSheet, {
  tableName: "GovernanceReviewV2Table",
  headers: ["复核项键", "层级", "文档键", "对象键", "问题类型", "当前判断", "证据页码", "建议处理", "迁移治理状态", "人工决定", "审核状态", "审核备注"],
  rows: governanceRows,
  widths: [16, 16, 14, 24, 32, 58, 18, 58, 24, 28, 18, 58],
  rowHeight: 48,
});

writeTableSheet(duplicateSheet, {
  tableName: "PhysicalDuplicateV2Table",
  headers: ["候选键", "原候选类型", "左文档", "左页码", "左页面键", "右文档", "右页码", "右页面键", "人工结论", "审核状态", "判定说明"],
  rows: duplicateRows,
  widths: [14, 26, 14, 10, 22, 14, 10, 22, 22, 18, 58],
  rowHeight: 38,
});

writeExistingValues(factSheet, "FactReviewV2Table", factValues, 42);
writeExistingValues(questionSheet, "QuestionReviewV2Table", questionValues, 42);
writeEnumSheet(enumSheet);
buildOverview(overview, documentRows.length, materialRows.length, pageRows.length, governanceRows.length, duplicateRows.length);

applyValidation(documentSheet, `N2:N${documentRows.length + 1}`, "'枚举V2'!$H$2:$H$7");
applyValidation(materialSheet, `K2:K${materialRows.length + 1}`, "'枚举V2'!$A$2:$A$7");
applyValidation(materialSheet, `L2:L${materialRows.length + 1}`, "'枚举V2'!$G$2:$G$6");
applyValidation(governanceSheet, `J2:J${governanceRows.length + 1}`, "'枚举V2'!$F$2:$F$8");
applyValidation(governanceSheet, `K2:K${governanceRows.length + 1}`, "'枚举V2'!$G$2:$G$6");

await fs.mkdir(path.dirname(outputPath), { recursive: true });
await fs.mkdir(previewDir, { recursive: true });
const output = await SpreadsheetFile.exportXlsx(workbook);
await output.save(outputPath);

const previewRanges = {
  "V2概览": "A1:H31",
  "文档结构": "A1:N12",
  "材料实例": "A1:M15",
  "页面结构": "A1:R12",
  "治理复核": `A1:L${Math.min(governanceRows.length + 1, 18)}`,
  "物理重复复核": "A1:K10",
  "事实标注": "A1:K12",
  "问题候选": "A1:I12",
  "枚举V2": "A1:H20",
};
for (const [sheetName, range] of Object.entries(previewRanges)) {
  const rendered = await workbook.render({ sheetName, range, scale: 1, format: "png" });
  await fs.writeFile(path.join(previewDir, `${sheetName}.png`), new Uint8Array(await rendered.arrayBuffer()));
}

const inspection = await workbook.inspect({
  kind: "workbook,sheet,table",
  maxChars: 16000,
  tableMaxRows: 4,
  tableMaxCols: 10,
  tableMaxCellChars: 100,
});
await fs.writeFile(path.join(previewDir, "inspection.ndjson"), inspection.ndjson ?? String(inspection), "utf8");
const formulaErrors = await workbook.inspect({
  kind: "match",
  searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
  options: { useRegex: true, maxResults: 100 },
  maxChars: 8000,
});
const errorText = formulaErrors.ndjson ?? String(formulaErrors);
await fs.writeFile(path.join(previewDir, "formula-errors.ndjson"), errorText, "utf8");
if (!errorText.includes("matched 0")) {
  throw new Error(`formula error scan found suspicious cells: ${errorText}`);
}

console.log(JSON.stringify({
  inputPath,
  outputPath,
  documentCount: documentRows.length,
  materialInstanceCount: materialRows.length,
  pageCount: pageRows.length,
  governanceReviewCount: governanceRows.length,
  duplicateCount: duplicateRows.length,
}, null, 2));


function governanceRowForMaterial(index, document, material, humanNotes) {
  const pageList = material.pages.map((item) => item.pageNo).join(",");
  const descriptions = {
    MISSING_REQUIRED: [`缺少标准结构要求的${materialLabel(material)}`, "补充原件或明确记录缺失，不得由其他材料自动补全"],
    SUBSTITUTE: [`使用${materialLabel(material)}的非标准替代材料`, "保留并标记替代来源，内容检索时允许作为成绩证据但不得冒充标准成绩单"],
    POTENTIALLY_REDUNDANT: [`存在额外的独立${materialLabel(material)}，不是重复拍摄`, "保留现状并按归档政策判断是否只需保留一份"],
    EXTRA_VALID: [`${materialLabel(material)}超出标准份数或页数，但属于真实材料`, "作为额外有效材料保留，并记录偏离标准结构的原因"],
    OUT_OF_STANDARD_SCOPE: [`存在标准结构之外的${materialLabel(material)}`, "保留原件；是否进入核心档案范围由人工确认"],
  };
  const [finding, suggestedAction] = descriptions[material.governanceStatus];
  return governanceRow(
    index,
    "MATERIAL_INSTANCE",
    document.documentKey,
    material.instanceKey,
    material.governanceStatus,
    finding,
    pageList,
    suggestedAction,
    material.governanceStatus,
    humanNotes,
  );
}

function governanceRow(index, level, documentKey, targetKey, issueType, finding, evidencePages, suggestion, governanceStatus, notes = "") {
  return [
    `GOV-${String(index).padStart(3, "0")}`,
    level,
    documentKey,
    targetKey,
    issueType,
    finding,
    evidencePages,
    suggestion,
    governanceStatus,
    "",
    "PENDING",
    notes,
  ];
}

function materialLabel(material) {
  const labels = {
    GRADUATION_ASSESSMENT: "毕业鉴定表",
    GRADE_RECORD: material.materialSubtype === "STANDARD_TRANSCRIPT" ? "成绩单" : "替代成绩材料",
    EXPERT_REVIEW: "专家评阅材料",
    DEGREE_AWARD_DECISION: "学位授予决定",
    SUPPLEMENTARY_MATERIAL: material.materialSubtype === "VOTING_BALLOT" ? "表决票" : "专家推荐书",
  };
  return labels[material.materialType] ?? material.materialType;
}

function buildOverview(sheet, documentCount, materialCount, pageCount, governanceCount, duplicateCount) {
  sheet.showGridLines = false;
  sheet.getRange("A1:H2").merge();
  sheet.getRange("A1:H2").values = [["Agent V2 多维档案标注审阅包"]];
  sheet.getRange("A1:H2").format = {
    fill: "#16324F",
    font: { bold: true, color: "#FFFFFF", size: 20 },
    verticalAlignment: "center",
  };
  sheet.getRange("A3:H3").merge();
  sheet.getRange("A3:H3").values = [["本工作簿由已完成人工审阅的 V1 迁移而来。材料类型、页面角色、材料实例和治理判断已经拆分；仅需处理“治理复核”中的少量异常项。"]];
  sheet.getRange("A3:H3").format = { fill: "#DCEAF5", font: { color: "#16324F", bold: true }, wrapText: true };

  sheet.getRange("A5:B5").values = [["数据规模", "数量"]];
  sheet.getRange("A6:A10").values = [["文档"], ["材料实例"], ["页面"], ["治理复核项"], ["物理重复候选"]];
  sheet.getRange("B6:B10").formulas = [
    [`=COUNTA('文档结构'!$A$2:$A$${documentCount + 1})`],
    [`=COUNTA('材料实例'!$A$2:$A$${materialCount + 1})`],
    [`=COUNTA('页面结构'!$A$2:$A$${pageCount + 1})`],
    [`=COUNTA('治理复核'!$A$2:$A$${governanceCount + 1})`],
    [`=COUNTA('物理重复复核'!$A$2:$A$${duplicateCount + 1})`],
  ];
  styleMiniTable(sheet.getRange("A5:B10"));

  sheet.getRange("D5:E5").values = [["复核进度", "待处理"]];
  sheet.getRange("D6:D8").values = [["治理复核"], ["材料实例"], ["文档结构"]];
  sheet.getRange("E6:E8").formulas = [
    [`=COUNTIF('治理复核'!$K$2:$K$${governanceCount + 1},"PENDING")`],
    [`=COUNTIF('材料实例'!$L$2:$L$${materialCount + 1},"PENDING")`],
    [`=COUNTIF('文档结构'!$K$2:$K$${documentCount + 1},"PENDING")`],
  ];
  styleMiniTable(sheet.getRange("D5:E8"));

  sheet.getRange("A12:H12").merge();
  sheet.getRange("A12:H12").values = [["V2 标注语义"]];
  sheet.getRange("A12:H12").format = headerFormat("#2D6A4F");
  const notes = [
    "材料类型回答“这是什么材料”，例如专家评阅；页面角色回答“这一页在材料中的作用”，例如封面。",
    "材料实例区分第1份、第2份评阅表；同模板封面属于不同实例，不等于重复扫描。",
    "治理判断只描述与标准结构的关系，不执行删除：额外真实、疑似冗余、替代材料和范围外材料分别记录。",
    "物理重复仅指同一张原件被重复拍摄或扫描；V1 的8个候选均已迁移为非重复。",
  ];
  notes.forEach((note, index) => {
    const row = 13 + index;
    sheet.getRange(`A${row}:H${row}`).merge();
    sheet.getRange(`A${row}:H${row}`).values = [[`${index + 1}. ${note}`]];
    sheet.getRange(`A${row}:H${row}`).format = { fill: "#F5F7FA", wrapText: true, verticalAlignment: "center" };
  });

  sheet.getRange("A19:D19").values = [["标准模板", "标准页数", "标准组成", "适用来源组"]];
  const templates = Object.values(TEMPLATE_DEFINITIONS);
  sheet.getRange(`A20:D${19 + templates.length}`).values = templates.map((template) => [
    template.name,
    template.expectedPageCount,
    template.composition,
    template.code,
  ]);
  styleMiniTable(sheet.getRange(`A19:D${19 + templates.length}`));
  sheet.getRange("A:A").format.columnWidth = 30;
  sheet.getRange("B:B").format.columnWidth = 16;
  sheet.getRange("C:C").format.columnWidth = 70;
  sheet.getRange("D:D").format.columnWidth = 30;
  sheet.freezePanes.freezeRows(3);
}

function writeExistingValues(sheet, tableName, values, rowHeight) {
  const headers = values[0].map((value) => String(value ?? ""));
  const rows = values.slice(1);
  writeTableSheet(sheet, {
    tableName,
    headers,
    rows,
    widths: headers.map((_, index) => index < 3 ? 18 : 38),
    rowHeight,
  });
}

function writeTableSheet(sheet, { tableName, headers, rows, widths, rowHeight = 36 }) {
  sheet.showGridLines = false;
  const matrix = [headers, ...rows];
  sheet.getRangeByIndexes(0, 0, matrix.length, headers.length).values = matrix;
  sheet.getRangeByIndexes(0, 0, matrix.length, headers.length).format = { verticalAlignment: "top", wrapText: true };
  sheet.getRangeByIndexes(0, 0, 1, headers.length).format = headerFormat("#16324F");
  sheet.getRangeByIndexes(0, 0, 1, headers.length).format.rowHeight = 30;
  if (rows.length > 0) sheet.getRangeByIndexes(1, 0, rows.length, headers.length).format.rowHeight = rowHeight;
  widths.forEach((width, index) => {
    sheet.getRangeByIndexes(0, index, matrix.length, 1).format.columnWidth = width;
  });
  sheet.freezePanes.freezeRows(1);
  sheet.freezePanes.freezeColumns(Math.min(2, headers.length));
  if (rows.length > 0) {
    const table = sheet.tables.add(`A1:${columnName(headers.length)}${matrix.length}`, true, tableName);
    table.style = "TableStyleMedium2";
    table.showFilterButton = true;
  }
}

function writeEnumSheet(sheet) {
  const columns = {
    "治理状态": ["EXPECTED", "SUBSTITUTE", "MISSING_REQUIRED", "EXTRA_VALID", "POTENTIALLY_REDUNDANT", "OUT_OF_STANDARD_SCOPE"],
    "材料类型": ["STUDENT_STATUS_RECORD", "GRADE_RECORD", "GRADUATION_ASSESSMENT", "EXPERT_REVIEW", "DEGREE_AWARD_DECISION", "SUPPLEMENTARY_MATERIAL"],
    "页面角色": ["COVER", "CONTENT", "CONTINUATION", "DECISION_PAGE"],
    "可读性": ["READABLE", "PARTIAL", "UNREADABLE"],
    "结构判断": ["STANDARD", "VARIANT", "INCOMPLETE", "EXTRA_OR_VARIANT"],
    "人工决定": ["ACCEPT_FINDING", "REJECT_FINDING", "KEEP_ALL", "KEEP_ONE_POLICY_REVIEW", "EXCLUDE_FROM_CORE", "RETAIN_AS_SUBSTITUTE", "NEEDS_FOLLOWUP"],
    "审核状态": ["PENDING", "MIGRATED_CONFIRMED", "CONFIRMED", "CORRECTED", "NEEDS_FOLLOWUP"],
    "数据分组": ["DEV", "ACCEPTANCE", "GENERALIZATION", "CHALLENGE_UNASSIGNED", "CHALLENGE_DEV", "CHALLENGE_ACCEPTANCE"],
  };
  const entries = Object.entries(columns);
  const maxRows = Math.max(...entries.map(([, values]) => values.length));
  const matrix = [entries.map(([name]) => name)];
  for (let row = 0; row < maxRows; row += 1) {
    matrix.push(entries.map(([, values]) => values[row] ?? ""));
  }
  sheet.showGridLines = false;
  sheet.getRangeByIndexes(0, 0, matrix.length, entries.length).values = matrix;
  sheet.getRangeByIndexes(0, 0, 1, entries.length).format = headerFormat("#2D6A4F");
  sheet.getRangeByIndexes(0, 0, matrix.length, entries.length).format.columnWidth = 28;
  sheet.freezePanes.freezeRows(1);
}

function rowsFromSheet(workbook, sheetName) {
  const values = workbook.worksheets.getItem(sheetName).getUsedRange(true).values;
  const headers = values[0].map((value) => String(value ?? ""));
  return values.slice(1).map((row) => Object.fromEntries(headers.map((header, index) => [header, row[index] ?? ""])));
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
    wrapText: true,
  };
  range.getRow(0).format = headerFormat("#4B6078");
}

function columnName(count) {
  let value = count;
  let result = "";
  while (value > 0) {
    value -= 1;
    result = String.fromCharCode(65 + (value % 26)) + result;
    value = Math.floor(value / 26);
  }
  return result;
}

function parseArgs(values) {
  const parsed = {};
  for (let index = 0; index < values.length; index += 1) {
    if (!values[index].startsWith("--")) continue;
    parsed[values[index].slice(2)] = values[index + 1];
    index += 1;
  }
  return parsed;
}

async function readJsonl(filePath) {
  return (await fs.readFile(filePath, "utf8"))
    .split(/\r?\n/)
    .filter(Boolean)
    .map((line) => JSON.parse(line));
}
