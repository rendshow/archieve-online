import sys
import logging


def main():
    if len(sys.argv) < 2:
        print("usage: rapidocr_ocr.py <image_path>", file=sys.stderr)
        return 2

    image_path = sys.argv[1]
    try:
        from rapidocr import RapidOCR
    except Exception as exc:
        print(f"missing rapidocr dependency: {exc}", file=sys.stderr)
        return 3

    try:
        logging.getLogger("RapidOCR").setLevel(logging.ERROR)
        ocr = RapidOCR()
        result = ocr(image_path)
    except Exception as exc:
        print(f"rapidocr failed: {exc}", file=sys.stderr)
        return 4

    if hasattr(result, "txts"):
        text = "\n".join(item for item in result.txts if item)
    else:
        if isinstance(result, tuple) and result:
            result = result[0]
        text = "\n".join(item[1] for item in result or [] if len(item) > 1 and item[1])
    print(text.strip())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
