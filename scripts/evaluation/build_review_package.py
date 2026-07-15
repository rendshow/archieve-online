import argparse
import hashlib
import itertools
import json
import re
import shutil
import subprocess
import sys
from collections import Counter, defaultdict
from datetime import datetime
from difflib import SequenceMatcher
from pathlib import Path

from PIL import Image, ImageDraw


SCRIPT_DIR = Path(__file__).resolve().parent
OCR_SCRIPT_DIR = SCRIPT_DIR.parent / "ocr"
sys.path.insert(0, str(OCR_SCRIPT_DIR))

from rapidocr_adapter import create_engine, recognize  # noqa: E402


MANIFEST_VERSION = 1
SECTION_TYPES = (
    "COVER",
    "STUDENT_STATUS",
    "TRANSCRIPT",
    "IDEOLOGICAL_ASSESSMENT",
    "EXPERT_REVIEW",
    "DEGREE_AWARD_DECISION",
    "GRADUATION_DEGREE_STATUS",
    "OTHER_GRADE_MATERIAL",
    "INVALID_PAGE",
    "DUPLICATE_PAGE",
    "OTHER",
)


def main():
    parser = argparse.ArgumentParser(description="Build a private Agent V2 PDF review package.")
    parser.add_argument("--source", required=True, help="Root folder containing evaluation PDFs.")
    parser.add_argument("--output", default="evaluation/private/review-package")
    parser.add_argument("--pdfinfo", default="pdfinfo")
    parser.add_argument("--pdftoppm", default="pdftoppm")
    parser.add_argument("--dpi", type=int, default=150)
    parser.add_argument("--limit", type=int, default=0, help="Limit PDF count for a smoke run. 0 means all.")
    parser.add_argument("--skip-ocr", action="store_true")
    args = parser.parse_args()

    source_root = Path(args.source).resolve()
    output_root = Path(args.output).resolve()
    if not source_root.is_dir():
        raise SystemExit(f"source directory does not exist: {source_root}")
    if args.dpi < 72:
        raise SystemExit("dpi must be at least 72")

    pdfinfo = resolve_executable(args.pdfinfo)
    pdftoppm = resolve_executable(args.pdftoppm)
    output_root.mkdir(parents=True, exist_ok=True)
    (output_root / "pages").mkdir(exist_ok=True)
    (output_root / "ocr").mkdir(exist_ok=True)
    (output_root / "contact-sheets").mkdir(exist_ok=True)

    pdf_paths = sorted(source_root.rglob("*.pdf"), key=lambda path: natural_key(str(path.relative_to(source_root))))
    if args.limit > 0:
        pdf_paths = pdf_paths[:args.limit]
    document_records = build_document_records(pdf_paths, source_root, pdfinfo)
    assign_proposed_splits(document_records)

    engine = None if args.skip_ocr else create_engine()
    page_records = []
    fact_records = []
    question_records = []

    for document_index, document in enumerate(document_records, start=1):
        pdf_path = source_root / Path(document["relativePath"])
        page_dir = output_root / "pages" / document["documentKey"]
        ocr_dir = output_root / "ocr" / document["documentKey"]
        contact_path = output_root / "contact-sheets" / f"{document['documentKey']}.jpg"
        rendered_pages = render_pdf(pdf_path, page_dir, document["pageCount"], pdftoppm, args.dpi)
        create_contact_sheet(rendered_pages, contact_path, document["documentKey"])
        document["contactSheetPath"] = str(contact_path)

        document_pages = []
        for page_number, image_path in enumerate(rendered_pages, start=1):
            ocr_path = ocr_dir / f"page-{page_number:04d}.json"
            ocr_result = load_or_run_ocr(engine, image_path, ocr_path, args.skip_ocr)
            image_info = image_fingerprints(image_path)
            text = ocr_result.get("text", "")
            page_record = {
                "pageKey": f"{document['documentKey']}-P{page_number:04d}",
                "documentKey": document["documentKey"],
                "pageNo": page_number,
                "imagePath": str(image_path),
                "ocrPath": str(ocr_path),
                "sectionCandidate": classify_section(text),
                "reviewedSection": "",
                "qualityCandidate": classify_quality(ocr_result),
                "ocrText": text,
                "ocrPreview": compact_text(text, 500),
                "ocrLineCount": int(ocr_result.get("lineCount", 0) or 0),
                "ocrAverageConfidence": ocr_result.get("averageConfidence"),
                "ocrElapsedSeconds": ocr_result.get("elapsedSeconds", 0.0),
                "normalizedImageSha256": image_info["normalizedImageSha256"],
                "dHash": image_info["dHash"],
                "imageWidth": image_info["imageWidth"],
                "imageHeight": image_info["imageHeight"],
                "duplicateCandidateCount": 0,
                "reviewStatus": "PENDING",
                "reviewNotes": "",
            }
            page_records.append(page_record)
            document_pages.append(page_record)

        document["ocrReadyPages"] = sum(1 for page in document_pages if page["ocrLineCount"] > 0)
        document["contactSheetPath"] = str(contact_path)
        fact_records.extend(extract_fact_candidates(document, document_pages))
        question_records.extend(build_question_candidates(document))
        print(
            f"[{document_index}/{len(document_records)}] {document['documentKey']} "
            f"pages={document['pageCount']} ocr={document['ocrReadyPages']}",
            flush=True,
        )

    duplicate_records = find_duplicate_candidates(page_records)
    duplicate_counts = Counter()
    for candidate in duplicate_records:
        duplicate_counts[candidate["leftPageKey"]] += 1
        duplicate_counts[candidate["rightPageKey"]] += 1
    for page in page_records:
        page["duplicateCandidateCount"] = duplicate_counts[page["pageKey"]]

    write_jsonl(output_root / "documents.jsonl", document_records)
    write_jsonl(output_root / "pages.jsonl", page_records)
    write_jsonl(output_root / "facts.jsonl", fact_records)
    write_jsonl(output_root / "duplicate_candidates.jsonl", duplicate_records)
    write_jsonl(output_root / "question_candidates.jsonl", question_records)

    summary = build_summary(source_root, output_root, args, document_records, page_records, duplicate_records)
    write_json(output_root / "package_summary.json", summary)
    print(f"package={output_root}")
    print(json.dumps(summary, ensure_ascii=False, indent=2))


def build_document_records(pdf_paths, source_root, pdfinfo):
    records = []
    for index, pdf_path in enumerate(pdf_paths, start=1):
        info = inspect_pdf(pdf_path, pdfinfo)
        relative_path = pdf_path.relative_to(source_root)
        source_group = classify_source_group(relative_path)
        records.append({
            "documentKey": f"DOC-{index:04d}",
            "sourceGroup": source_group,
            "proposedSplit": "UNASSIGNED",
            "relativePath": relative_path.as_posix(),
            "fileName": pdf_path.name,
            "fileNamePersonCandidate": person_from_filename(pdf_path.stem),
            "reviewedPersonName": "",
            "expectedPageCountFromName": expected_pages_from_filename(pdf_path.stem),
            "pageCount": info["pages"],
            "encrypted": info["encrypted"],
            "fileSizeBytes": pdf_path.stat().st_size,
            "sha256": sha256_file(pdf_path),
            "ocrReadyPages": 0,
            "contactSheetPath": "",
            "reviewStatus": "PENDING",
            "reviewNotes": "",
        })
    return records


def classify_source_group(relative_path):
    value = relative_path.as_posix()
    if "基线 20_2+19_4/1988农艺中专" in value:
        return "BASELINE_2PAGE"
    if "基线 20_2+19_4/1999军需自动化本科" in value:
        return "BASELINE_4PAGE"
    if value.startswith("混杂泛化组/"):
        return "MIXED_GENERALIZATION"
    if value.startswith("边界组/2001届博士/"):
        return "BOUNDARY_DOCTOR"
    if value.startswith("边界组/2001届硕士/"):
        return "BOUNDARY_MASTER"
    return "UNCLASSIFIED"


def assign_proposed_splits(documents):
    grouped = defaultdict(list)
    for document in documents:
        grouped[document["sourceGroup"]].append(document)
    for source_group, rows in grouped.items():
        rows.sort(key=lambda row: natural_key(row["fileName"]))
        for index, row in enumerate(rows):
            if source_group == "BASELINE_2PAGE":
                row["proposedSplit"] = "DEV" if index < 14 else "ACCEPTANCE"
            elif source_group == "BASELINE_4PAGE":
                row["proposedSplit"] = "DEV" if index < 13 else "ACCEPTANCE"
            elif source_group == "MIXED_GENERALIZATION":
                row["proposedSplit"] = "GENERALIZATION"
            elif source_group.startswith("BOUNDARY_"):
                row["proposedSplit"] = "CHALLENGE_UNASSIGNED"
            else:
                row["proposedSplit"] = "UNASSIGNED"


def inspect_pdf(pdf_path, pdfinfo):
    result = run_external(pdfinfo, str(pdf_path))
    pages_match = re.search(r"(?m)^Pages:\s+(\d+)", result.stdout)
    encrypted_match = re.search(r"(?m)^Encrypted:\s+(\w+)", result.stdout)
    if not pages_match:
        raise RuntimeError(f"pdfinfo did not return page count: {pdf_path}")
    return {
        "pages": int(pages_match.group(1)),
        "encrypted": encrypted_match is not None and encrypted_match.group(1).lower() == "yes",
    }


def render_pdf(pdf_path, page_dir, expected_pages, pdftoppm, dpi):
    page_dir.mkdir(parents=True, exist_ok=True)
    existing = sorted(page_dir.glob("page-*.jpg"), key=lambda path: natural_key(path.name))
    if len(existing) == expected_pages:
        return existing

    for path in page_dir.glob("*.jpg"):
        path.unlink()
    prefix = page_dir / "render"
    run_external(
        pdftoppm,
        "-jpeg",
        "-r",
        str(dpi),
        "-jpegopt",
        "quality=85",
        str(pdf_path),
        str(prefix),
    )
    rendered = sorted(page_dir.glob("render-*.jpg"), key=lambda path: natural_key(path.name))
    if len(rendered) != expected_pages:
        raise RuntimeError(
            f"rendered page count mismatch for {pdf_path}: expected {expected_pages}, got {len(rendered)}"
        )
    final_paths = []
    for page_number, rendered_path in enumerate(rendered, start=1):
        final_path = page_dir / f"page-{page_number:04d}.jpg"
        rendered_path.replace(final_path)
        final_paths.append(final_path)
    return final_paths


def create_contact_sheet(page_paths, output_path, document_key):
    if output_path.exists():
        return
    columns = 5
    tile_width = 220
    tile_height = 300
    rows = (len(page_paths) + columns - 1) // columns
    sheet = Image.new("RGB", (columns * tile_width, rows * tile_height), "#E5E7EB")
    draw = ImageDraw.Draw(sheet)
    for index, page_path in enumerate(page_paths):
        with Image.open(page_path) as source:
            image = source.convert("RGB")
            image.thumbnail((tile_width - 20, tile_height - 40), Image.Resampling.LANCZOS)
        left = (index % columns) * tile_width
        top = (index // columns) * tile_height
        image_left = left + (tile_width - image.width) // 2
        image_top = top + 28
        sheet.paste(image, (image_left, image_top))
        draw.text((left + 8, top + 8), f"{document_key} P{index + 1}", fill="#111827")
    output_path.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(output_path, "JPEG", quality=86)


def load_or_run_ocr(engine, image_path, ocr_path, skip_ocr):
    if ocr_path.exists():
        return json.loads(ocr_path.read_text(encoding="utf-8"))
    if skip_ocr:
        return {
            "text": "",
            "lines": [],
            "lineCount": 0,
            "averageConfidence": None,
            "elapsedSeconds": 0.0,
            "status": "SKIPPED",
        }
    ocr_path.parent.mkdir(parents=True, exist_ok=True)
    result = recognize(engine, image_path)
    result["status"] = "READY"
    write_json(ocr_path, result)
    return result


def image_fingerprints(image_path):
    with Image.open(image_path) as source:
        image = source.convert("L")
        width, height = image.size
        normalized = image.resize((256, 256), Image.Resampling.LANCZOS)
        normalized_hash = hashlib.sha256(normalized.tobytes()).hexdigest()
        dhash_image = image.resize((9, 8), Image.Resampling.LANCZOS)
        pixels = list(dhash_image.get_flattened_data())
        bits = []
        for row in range(8):
            offset = row * 9
            for column in range(8):
                bits.append(pixels[offset + column] > pixels[offset + column + 1])
        dhash_value = 0
        for bit in bits:
            dhash_value = (dhash_value << 1) | int(bit)
    return {
        "normalizedImageSha256": normalized_hash,
        "dHash": f"{dhash_value:016x}",
        "imageWidth": width,
        "imageHeight": height,
    }


def classify_section(text):
    compact = re.sub(r"\s+", "", text or "")
    if re.search(r"授予.{0,8}(博士|硕士|学士)?学位(决定|决议)书?|学位评定委员会.{0,12}(决定|决议)", compact):
        return "DEGREE_AWARD_DECISION"
    if re.search(r"学位论文评阅|论文评阅登记|专家评阅|评阅意见", compact):
        return "EXPERT_REVIEW"
    if re.search(r"学习成绩|成绩登记|成绩表|课程成绩|培养成绩|课程名称.{0,30}(成绩|学分)", compact):
        return "TRANSCRIPT"
    if re.search(r"学生.{0,2}籍表|学籍表|学籍管理登记", compact):
        return "STUDENT_STATUS"
    if re.search(r"思想鉴定|思想品德|政治思想", compact):
        return "IDEOLOGICAL_ASSESSMENT"
    if re.search(r"(毕业时间|学历证书).{0,160}学位证书|准予毕业.{0,80}(审核意见|学位证书)", compact):
        return "GRADUATION_DEGREE_STATUS"
    if len(compact) < 25:
        return "COVER"
    return "OTHER"


def classify_quality(ocr_result):
    line_count = int(ocr_result.get("lineCount", 0) or 0)
    confidence = ocr_result.get("averageConfidence")
    text_length = len(re.sub(r"\s+", "", ocr_result.get("text", "")))
    if line_count == 0:
        return "NO_TEXT"
    if confidence is not None and confidence < 0.75:
        return "LOW_CONFIDENCE"
    if text_length < 25:
        return "LOW_TEXT"
    return "NORMAL"


def extract_fact_candidates(document, pages):
    facts = []
    person_name = document.get("fileNamePersonCandidate") or ""
    evidence_page = next((page for page in pages if person_name and person_name in page["ocrText"]), None)
    facts.append({
        "factKey": f"{document['documentKey']}-F001",
        "documentKey": document["documentKey"],
        "pageNo": evidence_page["pageNo"] if evidence_page else None,
        "fieldType": "PERSON_NAME",
        "qualifier": "",
        "candidateValue": person_name,
        "reviewedValue": "",
        "candidateSource": "FILENAME_AND_OCR" if evidence_page else "FILENAME_ONLY",
        "evidenceText": evidence_snippet(evidence_page["ocrText"], person_name) if evidence_page else "",
        "reviewStatus": "PENDING",
        "reviewNotes": "",
    })

    seen_student_numbers = set()
    for page in pages:
        labels = ["学号"]
        if page["sectionCandidate"] == "STUDENT_STATUS":
            labels.append("编号")
        label_pattern = "|".join(labels)
        for match in re.finditer(rf"(?:{label_pattern})[:：\s]*([A-Za-z]?\d{{5,}})", page["ocrText"], re.IGNORECASE):
            value = match.group(1)
            if value in seen_student_numbers:
                continue
            seen_student_numbers.add(value)
            facts.append({
                "factKey": f"{document['documentKey']}-F{len(facts) + 1:03d}",
                "documentKey": document["documentKey"],
                "pageNo": page["pageNo"],
                "fieldType": "STUDENT_NO",
                "qualifier": "",
                "candidateValue": value,
                "reviewedValue": "",
                "candidateSource": "OCR_RULE",
                "evidenceText": evidence_snippet(page["ocrText"], value),
                "reviewStatus": "PENDING",
                "reviewNotes": "",
            })

    seen_dates = set()
    for page in pages:
        if page["sectionCandidate"] != "DEGREE_AWARD_DECISION":
            continue
        for match in re.finditer(r"((?:19|20)\d{2})[年./-](\d{1,2})[月./-](\d{1,2})日?", page["ocrText"]):
            normalized = f"{int(match.group(1)):04d}-{int(match.group(2)):02d}-{int(match.group(3)):02d}"
            qualifier = classify_date_qualifier(page["ocrText"], match.start(), match.end())
            date_key = (qualifier, normalized)
            if date_key in seen_dates:
                continue
            seen_dates.add(date_key)
            facts.append({
                "factKey": f"{document['documentKey']}-F{len(facts) + 1:03d}",
                "documentKey": document["documentKey"],
                "pageNo": page["pageNo"],
                "fieldType": "DATE",
                "qualifier": qualifier,
                "candidateValue": normalized,
                "reviewedValue": "",
                "candidateSource": "OCR_RULE",
                "evidenceText": evidence_snippet(page["ocrText"], match.group(0)),
                "reviewStatus": "PENDING",
                "reviewNotes": "",
            })
    return facts


def build_question_candidates(document):
    person_name = document.get("fileNamePersonCandidate") or "待确认姓名"
    return [{
        "questionKey": f"Q-{document['documentKey']}",
        "questionType": "LOCATE_DOCUMENT",
        "capability": "DOCUMENT_DISCOVERY",
        "scopeType": "FOLDER_RECURSIVE",
        "scopePath": document["sourceGroup"],
        "question": f"帮我找一下{person_name}的档案",
        "expectedStatus": "COMPLETED",
        "expectedDocumentKeys": [document["documentKey"]],
        "expectedFacts": [],
        "expectedAnswer": document["fileName"],
        "expectedEvidence": f"{document['documentKey']} | {document['relativePath']}",
        "expectedOutcome": "ANSWERED",
        "reviewStatus": "PENDING",
        "reviewNotes": "",
    }]


def classify_date_qualifier(text, start, end):
    before = re.sub(r"\s+", "", text[max(0, start - 140):start])
    if re.search(r"入学(时间|日期).{0,20}$", before):
        return "ADMISSION_DATE"
    if re.search(r"填发日期.{0,20}$", before):
        return "DEGREE_CERTIFICATE_ISSUE_DATE"
    if re.search(r"(同意|建议)?授予.{0,30}学位|学位评定委.{0,40}(主席|决议)", before):
        return "DEGREE_AWARD_DATE"
    return "DATE_UNCLASSIFIED"


def find_duplicate_candidates(pages):
    candidates = []
    seen_pairs = set()
    by_normalized_hash = defaultdict(list)
    by_document = defaultdict(list)
    for page in pages:
        by_normalized_hash[page["normalizedImageSha256"]].append(page)
        by_document[page["documentKey"]].append(page)

    for matching_pages in by_normalized_hash.values():
        if len(matching_pages) < 2:
            continue
        for left, right in itertools.combinations(matching_pages, 2):
            add_duplicate_candidate(candidates, seen_pairs, left, right, "EXACT_RENDERED_PAGE", 0)

    for document_pages in by_document.values():
        for left, right in itertools.combinations(document_pages, 2):
            pair = tuple(sorted((left["pageKey"], right["pageKey"])))
            if pair in seen_pairs:
                continue
            distance = hamming_distance(left["dHash"], right["dHash"])
            if distance > 4:
                continue
            similarity = text_similarity(left["ocrText"], right["ocrText"])
            if similarity >= 0.98:
                add_duplicate_candidate(
                    candidates,
                    seen_pairs,
                    left,
                    right,
                    "NEAR_DUPLICATE_PAGE",
                    distance,
                    similarity,
                )
    return candidates


def add_duplicate_candidate(candidates, seen_pairs, left, right, candidate_type, distance, similarity=None):
    pair = tuple(sorted((left["pageKey"], right["pageKey"])))
    if pair in seen_pairs:
        return
    seen_pairs.add(pair)
    candidates.append({
        "candidateKey": f"DUP-{len(candidates) + 1:04d}",
        "candidateType": candidate_type,
        "scope": "SAME_DOCUMENT" if left["documentKey"] == right["documentKey"] else "CROSS_DOCUMENT",
        "leftDocumentKey": left["documentKey"],
        "leftPageKey": left["pageKey"],
        "leftPageNo": left["pageNo"],
        "rightDocumentKey": right["documentKey"],
        "rightPageKey": right["pageKey"],
        "rightPageNo": right["pageNo"],
        "dHashDistance": distance,
        "ocrTextSimilarity": similarity if similarity is not None else text_similarity(left["ocrText"], right["ocrText"]),
        "reviewedVerdict": "",
        "reviewStatus": "PENDING",
        "reviewNotes": "",
    })


def build_summary(source_root, output_root, args, documents, pages, duplicates):
    return {
        "manifestVersion": MANIFEST_VERSION,
        "generatedAt": datetime.now().astimezone().isoformat(timespec="seconds"),
        "sourceRoot": str(source_root),
        "outputRoot": str(output_root),
        "renderDpi": args.dpi,
        "ocrEnabled": not args.skip_ocr,
        "documentCount": len(documents),
        "pageCount": len(pages),
        "sourceGroupCounts": dict(sorted(Counter(row["sourceGroup"] for row in documents).items())),
        "proposedSplitCounts": dict(sorted(Counter(row["proposedSplit"] for row in documents).items())),
        "sectionCandidateCounts": dict(sorted(Counter(row["sectionCandidate"] for row in pages).items())),
        "qualityCandidateCounts": dict(sorted(Counter(row["qualityCandidate"] for row in pages).items())),
        "duplicateCandidateCount": len(duplicates),
    }


def person_from_filename(stem):
    cleaned = re.sub(r"_等\d+张$", "", stem)
    cleaned = re.sub(r"-\d+$", "", cleaned)
    matches = re.findall(r"[\u4e00-\u9fff]{2,4}", cleaned)
    return matches[-1] if matches else ""


def expected_pages_from_filename(stem):
    match = re.search(r"等(\d+)张", stem)
    return int(match.group(1)) if match else None


def evidence_snippet(text, value, radius=80):
    compact = compact_text(text, 4000)
    index = compact.find(value) if value else -1
    if index < 0:
        return compact[: radius * 2]
    return compact[max(0, index - radius): index + len(value) + radius]


def text_similarity(left, right):
    left_text = re.sub(r"\s+", "", left or "")
    right_text = re.sub(r"\s+", "", right or "")
    if not left_text or not right_text:
        return 0.0
    return round(SequenceMatcher(None, left_text, right_text).ratio(), 6)


def hamming_distance(left_hex, right_hex):
    return (int(left_hex, 16) ^ int(right_hex, 16)).bit_count()


def compact_text(value, limit):
    compact = re.sub(r"\s+", " ", value or "").strip()
    return compact if len(compact) <= limit else compact[:limit]


def sha256_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def natural_key(value):
    return [int(part) if part.isdigit() else part.lower() for part in re.split(r"(\d+)", str(value))]


def resolve_executable(value):
    path = Path(value)
    if path.is_file():
        return str(path.resolve())
    resolved = shutil.which(value)
    if resolved:
        return resolved
    raise SystemExit(f"executable not found: {value}")


def run_external(executable, *args):
    if Path(executable).suffix.lower() in {".cmd", ".bat"}:
        command_line = subprocess.list2cmdline([executable, *args])
        command = ["cmd", "/d", "/s", "/c", command_line]
    else:
        command = [executable, *args]
    result = subprocess.run(
        command,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )
    if result.returncode != 0:
        raise RuntimeError(
            f"external command failed ({result.returncode}): {executable}\n{result.stderr.strip()}"
        )
    return result


def write_jsonl(path, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as output:
        for row in rows:
            output.write(json.dumps(row, ensure_ascii=False) + "\n")


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
