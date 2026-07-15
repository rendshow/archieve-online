import logging
from pathlib import Path


def create_engine():
    try:
        from rapidocr import RapidOCR
    except Exception as exc:
        raise RuntimeError(f"missing rapidocr dependency: {exc}") from exc

    logging.getLogger("RapidOCR").setLevel(logging.ERROR)
    return RapidOCR()


def recognize(engine, image_path):
    path = Path(image_path)
    try:
        result = engine(str(path))
    except Exception as exc:
        raise RuntimeError(f"rapidocr failed: {exc}") from exc

    if hasattr(result, "txts"):
        texts = list(result.txts or [])
        scores = list(result.scores or [])
        boxes = result.boxes.tolist() if getattr(result, "boxes", None) is not None else []
        elapsed = float(getattr(result, "elapse", 0.0) or 0.0)
    else:
        legacy_result = result[0] if isinstance(result, tuple) and result else result
        legacy_result = legacy_result or []
        texts = [item[1] for item in legacy_result if len(item) > 1 and item[1]]
        scores = [float(item[2]) for item in legacy_result if len(item) > 2]
        boxes = [item[0] for item in legacy_result if item]
        elapsed = 0.0

    lines = []
    for index, text in enumerate(texts):
        if not text:
            continue
        score = float(scores[index]) if index < len(scores) else None
        box = boxes[index] if index < len(boxes) else None
        lines.append({
            "text": str(text),
            "score": score,
            "box": box,
        })

    valid_scores = [line["score"] for line in lines if line["score"] is not None]
    average_confidence = sum(valid_scores) / len(valid_scores) if valid_scores else None
    return {
        "text": "\n".join(line["text"] for line in lines).strip(),
        "lines": lines,
        "lineCount": len(lines),
        "averageConfidence": average_confidence,
        "elapsedSeconds": elapsed,
    }
