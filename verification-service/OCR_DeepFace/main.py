import shutil

from fastapi import FastAPI, UploadFile, File
import numpy as np
import cv2
import os
from ultralytics import YOLO
from face_match import face_match

app = FastAPI()

object_model = YOLO("weights/detect_odjects.pt")
digit_model = YOLO("weights/detect_id.pt")


def safe_filename(name):
    return os.path.basename(name).replace(" ", "_")


def preprocess_for_yolo(img):
    h, w = img.shape[:2]
    if w < 1000:
        img = cv2.resize(img, (1280, 819), interpolation=cv2.INTER_CUBIC)
    return img



def validate_nid(nid):
    return nid is not None and len(nid) == 14 and nid.isdigit()


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


def try_extract(img, pad_factor, filename):

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


            if cls_name == "photo":
                if crop is not None and crop.size != 0:
                    os.makedirs("static", exist_ok=True)
                    cv2.imwrite(f"static/{filename}", crop)


            if cls_name == "nid":
                nid = detect_digits(crop)

                if validate_nid(nid):
                    return nid

                best = nid

    return best


def process_image(img, filename):

    img = preprocess_for_yolo(img)

    cv2.imwrite("debug_full.jpg", img)

    attempts = [0.20, 0.30, 0.40]

    best = ""

    for p in attempts:
        nid = try_extract(img, p, filename)

        print(f"try pad {p}: {nid}")

        if validate_nid(nid):
            return {"nid": nid}

        if len(nid) > len(best):
            best = nid

    return {"nid": best}


def detect_and_process(img, filename):

    rotations = [
        img,
        cv2.rotate(img, cv2.ROTATE_90_CLOCKWISE),
        cv2.rotate(img, cv2.ROTATE_180),
        cv2.rotate(img, cv2.ROTATE_90_COUNTERCLOCKWISE)
    ]

    best = ""

    for r in rotations:

        result = process_image(r, filename)
        nid = result["nid"]

        print("result:", nid)

        if validate_nid(nid):
            return nid

        if len(nid) > len(best):
            best = nid

    return best
def read_image(file_bytes):
    np_arr = np.frombuffer(file_bytes, np.uint8)
    img = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)

    if img is None:
        raise ValueError("Could not decode image")

    return img

@app.post("/extract-nid")
async def extract_nid(file: UploadFile = File(...)):

    image_bytes = await file.read()

    np_arr = np.frombuffer(image_bytes, np.uint8)
    img = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)

    if img is None:
        return {"error": "invalid image"}

    filename = safe_filename(file.filename)

    nid = detect_and_process(img, filename)

    return {
        "nid": nid,
        "valid": validate_nid(nid),
        "photo_url": f"http://127.0.0.1:8000/static/{filename}"
    }

@app.post("/face-match")
async def face_match_endpoint(
    id_image: UploadFile = File(...),
    selfie: UploadFile = File(...)
):

    id_bytes = await id_image.read()
    selfie_bytes = await selfie.read()

    img1 = read_image(id_bytes)
    img2 = read_image(selfie_bytes)

    return face_match(img1, img2, object_model=object_model)