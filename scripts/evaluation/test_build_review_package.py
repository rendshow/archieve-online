import sys
import unittest
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

import build_review_package as package  # noqa: E402


class ReviewPackageTest(unittest.TestCase):
    def test_extracts_person_name_from_known_filename_patterns(self):
        cases = {
            "蔡钰-1_等2张": "蔡钰",
            "2001-JX14\u202211\u2022616-10丁壮": "丁壮",
            "00151-1栾葳_等2张": "栾葳",
            "丁志明-1_等2张": "丁志明",
        }

        for filename, expected in cases.items():
            with self.subTest(filename=filename):
                self.assertEqual(expected, package.person_from_filename(filename))

    def test_classifies_scanned_student_status_variants(self):
        texts = [
            "中国人民解放军兽医大学 学生 籍表 学业期 88年级",
            "研究生学籍表 姓名 李四",
            "学籍管理登记 编号 001",
        ]

        for text in texts:
            with self.subTest(text=text):
                self.assertEqual("STUDENT_STATUS", package.classify_section(text))

    def test_classifies_transcript_and_degree_decision(self):
        self.assertEqual("TRANSCRIPT", package.classify_section("学习成绩登记 课程名称 成绩"))
        self.assertEqual(
            "TRANSCRIPT",
            package.classify_section("学号 3031999015 课程名称 学时 类别 成绩 学分 思想品德 良好"),
        )
        self.assertEqual(
            "DEGREE_AWARD_DECISION",
            package.classify_section("校学位评定委员会决定授予博士学位"),
        )
        self.assertEqual(
            "DEGREE_AWARD_DECISION",
            package.classify_section("授予博士学位决议书"),
        )
        self.assertEqual(
            "GRADUATION_DEGREE_STATUS",
            package.classify_section("毕业时间 2003年06月30日 学历证书编号 学位证书编号"),
        )

    def test_assigns_baseline_and_challenge_splits(self):
        documents = [
            {"sourceGroup": "BASELINE_2PAGE", "fileName": f"学生{i}.pdf"}
            for i in range(20)
        ]
        documents.extend([
            {"sourceGroup": "BOUNDARY_MASTER", "fileName": "边界1.pdf"},
            {"sourceGroup": "MIXED_GENERALIZATION", "fileName": "泛化1.pdf"},
        ])

        package.assign_proposed_splits(documents)

        baseline = [row for row in documents if row["sourceGroup"] == "BASELINE_2PAGE"]
        self.assertEqual(14, sum(row["proposedSplit"] == "DEV" for row in baseline))
        self.assertEqual(6, sum(row["proposedSplit"] == "ACCEPTANCE" for row in baseline))
        self.assertEqual("CHALLENGE_UNASSIGNED", documents[-2]["proposedSplit"])
        self.assertEqual("GENERALIZATION", documents[-1]["proposedSplit"])

    def test_classifies_date_context_without_overclaiming(self):
        text = "入学时间 1998.3.1 学位评定委员会决议同意授予博士学位 主席 2001年6月29日 填发日期 2001年6月30日"
        matches = list(package.re.finditer(r"((?:19|20)\d{2})[年./-](\d{1,2})[月./-](\d{1,2})日?", text))

        qualifiers = [package.classify_date_qualifier(text, match.start(), match.end()) for match in matches]

        self.assertEqual(
            ["ADMISSION_DATE", "DEGREE_AWARD_DATE", "DEGREE_CERTIFICATE_ISSUE_DATE"],
            qualifiers,
        )

    def test_generic_number_is_student_number_only_on_student_status_page(self):
        document = {"documentKey": "DOC-TEST", "fileNamePersonCandidate": "李四"}
        pages = [
            {
                "pageNo": 1,
                "sectionCandidate": "STUDENT_STATUS",
                "ocrText": "学籍表 姓名 李四 编号：88003",
            },
            {
                "pageNo": 2,
                "sectionCandidate": "GRADUATION_DEGREE_STATUS",
                "ocrText": "毕业证书编号 90498",
            },
        ]

        facts = package.extract_fact_candidates(document, pages)
        student_numbers = [fact["candidateValue"] for fact in facts if fact["fieldType"] == "STUDENT_NO"]

        self.assertEqual(["88003"], student_numbers)


if __name__ == "__main__":
    unittest.main()
