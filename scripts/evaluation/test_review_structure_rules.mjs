import assert from "node:assert/strict";
import test from "node:test";

import { buildDocumentStructure } from "./review_structure_rules.mjs";


function document(documentKey, sourceGroup, pageCount) {
  return { documentKey, sourceGroup, pageCount };
}

test("builds the standard doctoral 21-page structure", () => {
  const result = buildDocumentStructure(document("DOC-0060", "BOUNDARY_DOCTOR", 21));

  assert.equal(result.instances.filter((row) => row.materialType === "EXPERT_REVIEW").length, 5);
  assert.equal(result.instances.filter((row) => row.materialType === "DEGREE_AWARD_DECISION").length, 1);
  assert.equal(result.structureAssessment, "STANDARD");
});

test("distinguishes extra authentic materials from duplicate scans", () => {
  const result = buildDocumentStructure(document("DOC-0067", "BOUNDARY_DOCTOR", 27));

  assert.equal(result.instances.filter((row) => row.materialType === "GRADUATION_ASSESSMENT").length, 2);
  assert.equal(result.instances.filter((row) => row.materialType === "DEGREE_AWARD_DECISION").length, 2);
  assert.equal(result.instances.filter((row) => row.governanceStatus === "POTENTIALLY_REDUNDANT").length, 2);
  assert.equal(result.instances.some((row) => row.governanceStatus === "EXTRA_VALID"), true);
});

test("represents a missing transcript without consuming a page", () => {
  const result = buildDocumentStructure(document("DOC-0074", "BOUNDARY_DOCTOR", 21));
  const grade = result.instances.find((row) => row.materialType === "GRADE_RECORD");

  assert.deepEqual(grade.pages, []);
  assert.equal(grade.governanceStatus, "MISSING_REQUIRED");
  assert.equal(result.structureAssessment, "INCOMPLETE");
});

test("builds the standard master 8-page structure", () => {
  const result = buildDocumentStructure(document("DOC-0077", "BOUNDARY_MASTER", 8));

  assert.equal(result.instances.filter((row) => row.materialType === "EXPERT_REVIEW").length, 2);
  assert.equal(result.structureAssessment, "STANDARD");
});

test("keeps recommendation letters outside the standard structure", () => {
  const result = buildDocumentStructure(document("DOC-0079", "BOUNDARY_MASTER", 12));

  assert.equal(result.instances.filter((row) => row.materialSubtype === "EXPERT_RECOMMENDATION_LETTER").length, 3);
  assert.equal(result.instances.some((row) => row.governanceStatus === "MISSING_REQUIRED"), true);
  assert.equal(result.instances.some((row) => row.governanceStatus === "SUBSTITUTE"), true);
});

test("groups the bachelor cover and graduation decision into the student-status instance", () => {
  const result = buildDocumentStructure(document("DOC-0021", "BASELINE_4PAGE", 4));
  const studentStatus = result.instances.find((row) => row.materialType === "STUDENT_STATUS_RECORD");

  assert.deepEqual(studentStatus.pages.map((row) => row.pageNo), [1, 2, 4]);
  assert.deepEqual(studentStatus.pages.map((row) => row.role), ["COVER", "CONTENT", "DECISION_PAGE"]);
});
