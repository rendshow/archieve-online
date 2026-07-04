import sys
from pathlib import Path

MAX_EDGE = 2200
MAX_BYTES = 4_800_000
MIN_QUALITY = 60


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8")

    if len(sys.argv) < 3:
        print("usage: convert_heic.py <source.heic> <target.jpg>", file=sys.stderr)
        return 2

    source = Path(sys.argv[1])
    target = Path(sys.argv[2])
    try:
        from PIL import Image, ImageOps
        import pillow_heif
    except Exception as exc:
        print(f"missing HEIC dependency: {exc}", file=sys.stderr)
        return 3

    try:
        pillow_heif.register_heif_opener()
        with Image.open(source) as image:
            image = ImageOps.exif_transpose(image)
            image = image.convert("RGB")
            image.thumbnail((MAX_EDGE, MAX_EDGE), Image.Resampling.LANCZOS)
            target.parent.mkdir(parents=True, exist_ok=True)
            quality = 85
            while True:
                image.save(target, format="JPEG", quality=quality, optimize=True)
                if target.stat().st_size <= MAX_BYTES or quality <= MIN_QUALITY:
                    break
                quality -= 5
    except Exception as exc:
        print(f"convert HEIC failed: {exc}", file=sys.stderr)
        return 4

    print(str(target))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
