import argparse
import csv
import re
import shutil
import tempfile
import time
import zipfile
from pathlib import Path


IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".webp", ".bmp", ".tif", ".tiff"}
PERSON_PATTERN = re.compile(r"(?:作者姓名|学生姓名|姓名|申请人)[:：\s]*([\u4e00-\u9fa5]{2,4})")
REFERENCE_PATTERN = re.compile(r"^(.*?)(\d+)([\u4e00-\u9fa5]{2,4})$")


def main():
    parser = argparse.ArgumentParser(description="Run OCR naming dry-run for 1v1 image zip archives.")
    parser.add_argument("--input", required=True, help="Folder containing image zip archives.")
    parser.add_argument("--reference", required=True, help="Reference folder containing standard named PDFs.")
    parser.add_argument("--output", default="target/ocr-benchmark/1v1-report.csv", help="CSV report path.")
    parser.add_argument("--engine", default="rapidocr", choices=["rapidocr", "pytesseract", "none"])
    parser.add_argument("--pages", type=int, default=3, help="How many first images in each zip to OCR.")
    parser.add_argument("--limit", type=int, default=0, help="Limit zip count. 0 means all.")
    parser.add_argument("--lang", default="chi_sim+eng", help="OCR language for pytesseract.")
    args = parser.parse_args()

    input_dir = Path(args.input)
    reference_dir = Path(args.reference)
    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    convention = load_reference_convention(reference_dir)
    ocr = build_ocr(args.engine, args.lang)

    zip_files = sorted(input_dir.glob("*.zip"), key=lambda path: natural_key(path.name))
    if args.limit > 0:
        zip_files = zip_files[:args.limit]

    rows = []
    with tempfile.TemporaryDirectory(prefix="archive-ocr-benchmark-") as temp_root:
        temp_root = Path(temp_root)
        for index, zip_path in enumerate(zip_files, start=1):
            rows.append(process_zip(zip_path, temp_root, ocr, convention, index, args.pages))

    write_csv(output_path, rows)
    print(f"report={output_path.resolve()}")
    print(f"rows={len(rows)}")
    print(f"engine={ocr.name}")
    if ocr.error:
        print(f"engine_error={ocr.error}")


def process_zip(zip_path, temp_root, ocr, convention, sequence_no, page_limit):
    start = time.perf_counter()
    work_dir = temp_root / safe_stem(zip_path.stem)
    work_dir.mkdir(parents=True, exist_ok=True)
    image_paths = extract_images(zip_path, work_dir)
    selected_images = image_paths[:page_limit]
    pieces = []
    status = "ok"
    reason = ""

    if not selected_images:
        status = "no_image"
        reason = "zip 中没有识别到图片"
    elif not ocr.available:
        status = "engine_missing"
        reason = ocr.error
    else:
        for image_path in selected_images:
            text = ocr.recognize(image_path)
            if text:
                pieces.append(text)

    text = "\n".join(pieces).strip()
    detected_name = detect_person_name(text)
    if status == "ok" and not detected_name:
        status = "need_review"
        reason = "未从 OCR 文本中提取到姓名"
    suggested_name = convention.suggest(sequence_no, detected_name)
    elapsed_ms = int((time.perf_counter() - start) * 1000)
    return {
        "source_zip": zip_path.name,
        "image_count": len(image_paths),
        "ocr_pages": len(selected_images),
        "detected_name": detected_name or "",
        "suggested_name": suggested_name,
        "status": status,
        "reason": reason,
        "evidence": compact(text, 220),
        "elapsed_ms": elapsed_ms,
    }


def extract_images(zip_path, work_dir):
    image_paths = []
    with zipfile.ZipFile(zip_path) as archive:
        names = [
            name for name in archive.namelist()
            if Path(name.replace("\\", "/")).suffix.lower() in IMAGE_EXTS
        ]
        names.sort(key=natural_key)
        for order, name in enumerate(names, start=1):
            ext = Path(name.replace("\\", "/")).suffix.lower()
            target = work_dir / f"{order:04d}{ext}"
            with archive.open(name) as source, open(target, "wb") as output:
                shutil.copyfileobj(source, output)
            image_paths.append(target)
    return image_paths


def build_ocr(engine, lang):
    if engine == "none":
        return OcrEngine("none", False, "未指定 OCR 引擎", lambda path: "")
    if engine == "pytesseract":
        try:
            from PIL import Image
            import pytesseract
        except Exception as exc:
            return OcrEngine("pytesseract", False, str(exc), lambda path: "")
        return OcrEngine(
            "pytesseract",
            True,
            "",
            lambda path: pytesseract.image_to_string(Image.open(path), lang=lang).strip(),
        )
    try:
        try:
            from rapidocr import RapidOCR
            engine_instance = RapidOCR()
            return OcrEngine("rapidocr", True, "", lambda path: rapidocr_text(engine_instance, path))
        except Exception:
            from rapidocr_onnxruntime import RapidOCR
            engine_instance = RapidOCR()
            return OcrEngine("rapidocr_onnxruntime", True, "", lambda path: rapidocr_text(engine_instance, path))
    except Exception as exc:
        return OcrEngine("rapidocr", False, str(exc), lambda path: "")


def rapidocr_text(engine, image_path):
    result, _ = engine(str(image_path))
    if not result:
        return ""
    return "\n".join(item[1] for item in result if len(item) > 1 and item[1]).strip()


def load_reference_convention(reference_dir):
    pdfs = sorted(reference_dir.glob("*.pdf"), key=lambda path: natural_key(path.name))
    for pdf in pdfs:
        match = REFERENCE_PATTERN.match(strip_ext(pdf.name))
        if match:
            return NamingConvention(match.group(1), len(match.group(2)))
    folder_name = reference_dir.name
    return NamingConvention(folder_name + "-", 1)


def detect_person_name(text):
    if not text:
        return None
    compact_text = re.sub(r"\s+", "", text)
    match = PERSON_PATTERN.search(compact_text)
    if match:
        return match.group(1)
    return None


def write_csv(output_path, rows):
    fields = [
        "source_zip",
        "image_count",
        "ocr_pages",
        "detected_name",
        "suggested_name",
        "status",
        "reason",
        "evidence",
        "elapsed_ms",
    ]
    with open(output_path, "w", encoding="utf-8-sig", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)


def natural_key(value):
    return [int(part) if part.isdigit() else part.lower() for part in re.split(r"(\d+)", str(value))]


def strip_ext(filename):
    return str(Path(filename).with_suffix(""))


def compact(text, limit):
    value = re.sub(r"\s+", " ", text or "").strip()
    return value if len(value) <= limit else value[:limit]


def safe_stem(value):
    return re.sub(r'[\\/:*?"<>|\s]+', "_", value)[:80] or "zip"


class OcrEngine:
    def __init__(self, name, available, error, recognize):
        self.name = name
        self.available = available
        self.error = error
        self.recognize = recognize


class NamingConvention:
    def __init__(self, prefix, width):
        self.prefix = prefix
        self.width = width

    def suggest(self, sequence_no, person_name):
        name = person_name or "待识别姓名"
        number = str(sequence_no).zfill(self.width) if self.width > 1 else str(sequence_no)
        return f"{self.prefix}{number}{name}.pdf"


if __name__ == "__main__":
    main()
