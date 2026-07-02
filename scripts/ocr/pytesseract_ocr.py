import sys


def main():
    if len(sys.argv) < 2:
        print("usage: pytesseract_ocr.py <image_path> [languages]", file=sys.stderr)
        return 2

    image_path = sys.argv[1]
    languages = sys.argv[2] if len(sys.argv) > 2 else "chi_sim+eng"

    try:
        from PIL import Image
        import pytesseract
    except Exception as exc:
        print(f"missing python OCR dependency: {exc}", file=sys.stderr)
        return 3

    text = pytesseract.image_to_string(Image.open(image_path), lang=languages)
    print(text.strip())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
