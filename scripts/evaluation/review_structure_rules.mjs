export const TEMPLATE_DEFINITIONS = {
  BASELINE_2PAGE: {
    code: "BASELINE_2PAGE",
    name: "1988中专学籍成绩结构",
    expectedPageCount: 2,
    composition: "学籍表1页 + 成绩单1页",
  },
  BASELINE_4PAGE: {
    code: "BASELINE_4PAGE",
    name: "1999本科学籍成绩结构",
    expectedPageCount: 4,
    composition: "学生封面1页 + 学籍表1页 + 成绩单1页 + 准予毕业决定1页",
  },
  MIXED_GENERALIZATION: {
    code: "MIXED_GENERALIZATION",
    name: "混杂泛化学籍成绩结构",
    expectedPageCount: 2,
    composition: "学籍表1页 + 成绩单1页",
  },
  BOUNDARY_DOCTOR: {
    code: "BOUNDARY_DOCTOR",
    name: "博士学位档案标准结构",
    expectedPageCount: 21,
    composition: "毕业鉴定表2页 + 成绩单1页 + 评阅登记表3页×5份 + 学位授予决定3页",
  },
  BOUNDARY_MASTER: {
    code: "BOUNDARY_MASTER",
    name: "硕士学位档案标准结构",
    expectedPageCount: 8,
    composition: "毕业鉴定表2页 + 成绩单1页 + 评阅表1页×2份 + 学位授予决定3页",
  },
};

const DOCTOR_OVERRIDES = {
  "DOC-0062": { expertPageCounts: [3, 3, 3, 3, 4] },
  "DOC-0067": { assessmentCount: 2, expertPageCounts: [3, 3, 4, 3, 3], degreeCount: 2 },
  "DOC-0070": { gradeSubtype: "ALTERNATIVE_GRADE_EVIDENCE", expertPageCounts: [3, 3, 4, 3, 3] },
  "DOC-0072": { assessmentCount: 2, degreeCount: 2 },
  "DOC-0074": { gradeSubtype: "MISSING", expertPageCounts: [3, 3, 4, 3, 3] },
  "DOC-0076": { gradeSubtype: "ALTERNATIVE_GRADE_EVIDENCE" },
};

const MASTER_OVERRIDES = {
  "DOC-0078": { assessmentCount: 0, recommendationCount: 1 },
  "DOC-0079": { assessmentCount: 0, gradeSubtype: "ALTERNATIVE_GRADE_EVIDENCE", recommendationCount: 3 },
  "DOC-0081": { assessmentCount: 0, gradeSubtype: "ALTERNATIVE_GRADE_EVIDENCE", recommendationCount: 3 },
  "DOC-0082": { ballotCount: 1 },
  "DOC-0083": { gradeSubtype: "ALTERNATIVE_GRADE_EVIDENCE", expertCount: 3 },
  "DOC-0085": { expertCount: 3 },
};

export function buildDocumentStructure(document) {
  const template = TEMPLATE_DEFINITIONS[document.sourceGroup];
  if (!template) {
    throw new Error(`unsupported source group: ${document.sourceGroup}`);
  }

  let instances;
  if (document.sourceGroup === "BASELINE_2PAGE" || document.sourceGroup === "MIXED_GENERALIZATION") {
    instances = buildTwoPageStructure(document.documentKey);
  } else if (document.sourceGroup === "BASELINE_4PAGE") {
    instances = buildFourPageStructure(document.documentKey);
  } else if (document.sourceGroup === "BOUNDARY_DOCTOR") {
    instances = buildDoctorStructure(document.documentKey, DOCTOR_OVERRIDES[document.documentKey] ?? {});
  } else {
    instances = buildMasterStructure(document.documentKey, MASTER_OVERRIDES[document.documentKey] ?? {});
  }

  const assignedPages = instances.flatMap((instance) => instance.pages.map((page) => page.pageNo));
  const duplicates = assignedPages.filter((pageNo, index) => assignedPages.indexOf(pageNo) !== index);
  const actualPages = [...new Set(assignedPages)].sort((left, right) => left - right);
  const expectedActualPages = Array.from({ length: document.pageCount }, (_, index) => index + 1);
  if (duplicates.length > 0 || JSON.stringify(actualPages) !== JSON.stringify(expectedActualPages)) {
    throw new Error(
      `${document.documentKey} page allocation mismatch: assigned=${actualPages.join(",")} expected=${expectedActualPages.join(",")}`,
    );
  }

  const governanceStatuses = new Set(instances.map((instance) => instance.governanceStatus));
  let structureAssessment = "STANDARD";
  if (governanceStatuses.has("MISSING_REQUIRED")) {
    structureAssessment = "INCOMPLETE";
  } else if (
    governanceStatuses.has("POTENTIALLY_REDUNDANT") ||
    governanceStatuses.has("EXTRA_VALID") ||
    governanceStatuses.has("OUT_OF_STANDARD_SCOPE")
  ) {
    structureAssessment = "EXTRA_OR_VARIANT";
  } else if (governanceStatuses.has("SUBSTITUTE")) {
    structureAssessment = "VARIANT";
  }

  return {
    template,
    instances,
    structureAssessment,
    pageDelta: document.pageCount - template.expectedPageCount,
  };
}

function buildTwoPageStructure(documentKey) {
  return [
    instance(documentKey, "STUDENT_STATUS_RECORD", "STANDARD_STUDENT_STATUS", 1, [page(1, "CONTENT")], 1),
    instance(documentKey, "GRADE_RECORD", "STANDARD_TRANSCRIPT", 1, [page(2, "CONTENT")], 1),
  ];
}

function buildFourPageStructure(documentKey) {
  return [
    instance(
      documentKey,
      "STUDENT_STATUS_RECORD",
      "STUDENT_STATUS_WITH_GRADUATION_DECISION",
      1,
      [page(1, "COVER"), page(2, "CONTENT"), page(4, "DECISION_PAGE")],
      3,
    ),
    instance(documentKey, "GRADE_RECORD", "STANDARD_TRANSCRIPT", 1, [page(3, "CONTENT")], 1),
  ];
}

function buildDoctorStructure(documentKey, override) {
  const config = {
    assessmentCount: 1,
    gradeSubtype: "STANDARD_TRANSCRIPT",
    expertPageCounts: [3, 3, 3, 3, 3],
    degreeCount: 1,
    ...override,
  };
  const instances = [];
  let nextPage = 1;

  for (let index = 1; index <= config.assessmentCount; index += 1) {
    const governance = index === 1 ? "EXPECTED" : "POTENTIALLY_REDUNDANT";
    instances.push(instance(
      documentKey,
      "GRADUATION_ASSESSMENT",
      "GRADUATION_ASSESSMENT_FORM",
      index,
      [page(nextPage, "CONTENT"), page(nextPage + 1, "CONTINUATION")],
      2,
      governance,
    ));
    nextPage += 2;
  }

  if (config.gradeSubtype === "MISSING") {
    instances.push(instance(documentKey, "GRADE_RECORD", "STANDARD_TRANSCRIPT", 1, [], 1, "MISSING_REQUIRED"));
  } else {
    const governance = config.gradeSubtype === "STANDARD_TRANSCRIPT" ? "EXPECTED" : "SUBSTITUTE";
    instances.push(instance(
      documentKey,
      "GRADE_RECORD",
      config.gradeSubtype,
      1,
      [page(nextPage, "CONTENT")],
      1,
      governance,
    ));
    nextPage += 1;
  }

  config.expertPageCounts.forEach((pageCount, index) => {
    const pages = allocatePages(nextPage, pageCount, "COVER");
    const governance = pageCount === 3 ? "EXPECTED" : "EXTRA_VALID";
    instances.push(instance(documentKey, "EXPERT_REVIEW", "EXPERT_REVIEW_REGISTRATION", index + 1, pages, 3, governance));
    nextPage += pageCount;
  });

  for (let index = 1; index <= config.degreeCount; index += 1) {
    const governance = index === 1 ? "EXPECTED" : "POTENTIALLY_REDUNDANT";
    instances.push(instance(
      documentKey,
      "DEGREE_AWARD_DECISION",
      "DEGREE_AWARD_DECISION_FORM",
      index,
      allocatePages(nextPage, 3, "COVER"),
      3,
      governance,
    ));
    nextPage += 3;
  }
  return instances;
}

function buildMasterStructure(documentKey, override) {
  const config = {
    assessmentCount: 1,
    gradeSubtype: "STANDARD_TRANSCRIPT",
    ballotCount: 0,
    expertCount: 2,
    recommendationCount: 0,
    ...override,
  };
  const instances = [];
  let nextPage = 1;

  if (config.assessmentCount === 0) {
    instances.push(instance(
      documentKey,
      "GRADUATION_ASSESSMENT",
      "GRADUATION_ASSESSMENT_FORM",
      1,
      [],
      2,
      "MISSING_REQUIRED",
    ));
  } else {
    instances.push(instance(
      documentKey,
      "GRADUATION_ASSESSMENT",
      "GRADUATION_ASSESSMENT_FORM",
      1,
      [page(nextPage, "CONTENT"), page(nextPage + 1, "CONTINUATION")],
      2,
    ));
    nextPage += 2;
  }

  const gradeGovernance = config.gradeSubtype === "STANDARD_TRANSCRIPT" ? "EXPECTED" : "SUBSTITUTE";
  instances.push(instance(
    documentKey,
    "GRADE_RECORD",
    config.gradeSubtype,
    1,
    [page(nextPage, "CONTENT")],
    1,
    gradeGovernance,
  ));
  nextPage += 1;

  for (let index = 1; index <= config.ballotCount; index += 1) {
    instances.push(instance(
      documentKey,
      "SUPPLEMENTARY_MATERIAL",
      "VOTING_BALLOT",
      index,
      [page(nextPage, "CONTENT")],
      0,
      "OUT_OF_STANDARD_SCOPE",
    ));
    nextPage += 1;
  }

  for (let index = 1; index <= config.expertCount; index += 1) {
    const governance = index <= 2 ? "EXPECTED" : "EXTRA_VALID";
    instances.push(instance(
      documentKey,
      "EXPERT_REVIEW",
      "MASTER_EXPERT_REVIEW",
      index,
      [page(nextPage, "CONTENT")],
      1,
      governance,
    ));
    nextPage += 1;
  }

  instances.push(instance(
    documentKey,
    "DEGREE_AWARD_DECISION",
    "DEGREE_AWARD_DECISION_FORM",
    1,
    allocatePages(nextPage, 3, "COVER"),
    3,
  ));
  nextPage += 3;

  for (let index = 1; index <= config.recommendationCount; index += 1) {
    instances.push(instance(
      documentKey,
      "SUPPLEMENTARY_MATERIAL",
      "EXPERT_RECOMMENDATION_LETTER",
      index,
      [page(nextPage, "CONTENT"), page(nextPage + 1, "CONTINUATION")],
      0,
      "OUT_OF_STANDARD_SCOPE",
    ));
    nextPage += 2;
  }
  return instances;
}

function allocatePages(startPage, pageCount, firstRole) {
  return Array.from({ length: pageCount }, (_, index) => {
    let role = "CONTINUATION";
    if (index === 0) role = firstRole;
    else if (index === 1) role = "CONTENT";
    return page(startPage + index, role);
  });
}

function page(pageNo, role) {
  return { pageNo, role };
}

function instance(documentKey, materialType, materialSubtype, instanceNo, pages, expectedPageCount, governanceStatus = "EXPECTED") {
  const typeCode = {
    STUDENT_STATUS_RECORD: "SSR",
    GRADE_RECORD: "GRD",
    GRADUATION_ASSESSMENT: "GRA",
    EXPERT_REVIEW: "EXR",
    DEGREE_AWARD_DECISION: "DAD",
    SUPPLEMENTARY_MATERIAL: "SUP",
  }[materialType];
  return {
    instanceKey: `${documentKey}-${typeCode}-${String(instanceNo).padStart(2, "0")}`,
    documentKey,
    materialType,
    materialSubtype,
    instanceNo,
    pages,
    expectedPageCount,
    governanceStatus,
  };
}
