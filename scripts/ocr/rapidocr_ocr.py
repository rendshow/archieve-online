import argparse
import json
import logging
import sys

from rapidocr_adapter import create_engine, recognize


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8")

    parser = argparse.ArgumentParser(description="Run RapidOCR for one image.")
    parser.add_argument("image_path")
    parser.add_argument("--json", action="store_true", help="Output text boxes and confidence as JSON.")
    args = parser.parse_args()

    try:
        engine = create_engine()
    except Exception as exc:
        print(str(exc), file=sys.stderr)
        return 3

    try:
        logging.getLogger("RapidOCR").setLevel(logging.ERROR)
        result = recognize(engine, args.image_path)
    except Exception as exc:
        print(str(exc), file=sys.stderr)
        return 4

    if args.json:
        print(json.dumps(result, ensure_ascii=False))
    else:
        print(result["text"])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
