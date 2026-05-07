from ultralytics import YOLO
import cv2

object_model = YOLO("weights/detect_odjects.pt")
digit_model = YOLO("weights/detect_id.pt")


# ---------------------------
# YOLO INPUT
# ---------------------------
def preprocess_for_yolo(img):
    h, w = img.shape[:2]
    if w < 1000:
        img = cv2.resize(img, (1280, 819), interpolation=cv2.INTER_CUBIC)
    return img


# ---------------------------
# VALIDATION
# ---------------------------
def validate_nid(nid):
    return nid is not None and len(nid) == 14 and nid.isdigit()


# ---------------------------
# DIGIT EXTRACTION
# ---------------------------
def detect_digits(crop):
    results = digit_model(crop, conf=0.10, iou=0.3, verbose=False)

    digits = []

    for r in results:
        for box in r.boxes:
            cls = int(box.cls[0])
            x1, y1, x2, y2 = map(int, box.xyxy[0])
            cx = (x1 + x2) / 2

            digits.append((cx, str(cls)))

    if not digits:
        return ""

    digits.sort(key=lambda x: x[0])

    return "".join([d for _, d in digits])


# ---------------------------
# TRY ONE IMAGE VERSION
# ---------------------------
def try_extract(img, pad_factor):
    h, w = img.shape[:2]

    results = object_model(img)

    best = ""

    for r in results:
        for box in r.boxes:

            cls_name = r.names[int(box.cls[0])]

            x1, y1, x2, y2 = map(int, box.xyxy[0])

            w_box = x2 - x1
            h_box = y2 - y1

            pad_x = int(w_box * pad_factor)
            pad_y = int(h_box * pad_factor)

            x1 = max(0, x1 - pad_x)
            y1 = max(0, y1 - pad_y)
            x2 = min(w, x2 + pad_x)
            y2 = min(h, y2 + pad_y)

            crop = img[y1:y2, x1:x2]

            # ---------------------------
            # ⭐ SAVE PHOTO FIELD
            # ---------------------------
            if cls_name == "photo":
                if crop is not None and crop.size != 0:
                    cv2.imwrite("best_photo.jpg", crop)
                    print("📸 Saved best photo -> best_photo.jpg")

            # ---------------------------
            # NID FIELD
            # ---------------------------
            if cls_name == "nid":
                nid = detect_digits(crop)

                if validate_nid(nid):
                    return nid

                best = nid

    return best


# ---------------------------
# MULTI PASS PIPELINE
# ---------------------------
def process_image(path):
    img = cv2.imread(path)

    if img is None:
        raise ValueError("Image not found")

    img = preprocess_for_yolo(img)

    cv2.imwrite("debug_full.jpg", img)

    attempts = [0.20, 0.30, 0.40]

    best = ""

    for p in attempts:
        nid = try_extract(img, p)

        print(f"try pad {p}: {nid}")

        if validate_nid(nid):
            return {"nid": nid}

        if len(nid) > len(best):
            best = nid

    return {"nid": best}


# ---------------------------
# ROTATION MULTI PASS
# ---------------------------
def detect_and_process(path):
    img = cv2.imread(path)

    rotations = [
        img,
        cv2.rotate(img, cv2.ROTATE_90_CLOCKWISE),
        cv2.rotate(img, cv2.ROTATE_180),
        cv2.rotate(img, cv2.ROTATE_90_COUNTERCLOCKWISE)
    ]

    best = ""

    for i, r in enumerate(rotations):
        print(f"\n--- rotation {i} ---")

        temp = f"temp_{i}.jpg"
        cv2.imwrite(temp, r)

        result = process_image(temp)
        nid = result["nid"]

        print("result:", nid)

        if validate_nid(nid):
            return {"nid": nid}

        if len(nid) > len(best):
            best = nid

    return {"nid": best}


# ---------------------------
# RUN
# ---------------------------
if __name__ == "__main__":
    result = detect_and_process("dataset/55.jpg")

    print("\nFINAL RESULT:")
    print(result)