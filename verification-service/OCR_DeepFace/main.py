import os
import asyncio
from concurrent.futures import ThreadPoolExecutor

import cv2
import numpy as np
from fastapi import FastAPI, UploadFile, File
from ultralytics import YOLO

from face_match import face_match

app = FastAPI()

object_model = YOLO("weights/detect_odjects.pt")  # Model for detecting objects (ID card, photo)
digit_model = YOLO("weights/detect_id.pt")  # Model for detecting digits on ID card

DEBUG = os.environ.get("NID_DEBUG", "0") == "1"  # Check if debug mode is enabled via environment variable

_executor = ThreadPoolExecutor(max_workers=int(os.environ.get("NID_WORKERS", "2")))  # Thread pool for blocking operations


def safe_filename(name: str) -> str:
    """Convert filename to safe format by removing spaces and ensuring basename only"""
    return os.path.basename(name).replace(" ", "_")  # Get basename and replace spaces with underscores


def validate_nid(nid):
    """Validate NID (National ID) format: must be 14 digits"""
    return nid is not None and len(nid) == 14 and nid.isdigit()  # Check if NID is 14 digits


def preprocess_for_yolo(img):
    h, w = img.shape[:2]  # Get image height and width
    if w < 1000:
        scale = 1280 / w  # Calculate scale factor to reach 1280 width
        new_w, new_h = 1280, int(round(h * scale))  # Calculate new dimensions preserving aspect ratio
        img = cv2.resize(img, (new_w, new_h), interpolation=cv2.INTER_CUBIC)  # Resize image with cubic interpolation
    return img


def read_image(file_bytes):
    np_arr = np.frombuffer(file_bytes, np.uint8)  # Convert bytes to numpy array
    img = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)  # Decode image as color image
    if img is None:
        raise ValueError("Could not decode image")
    return img


def detect_digits(crop):
    """Extract digits from cropped NID area using YOLO digit detection model"""
    if crop is None or crop.size == 0:
        return ""
    # crop: input image | conf: confidence threshold | iou: NMS IoU threshold | verbose: disable console output
    results = digit_model(crop, conf=0.10, iou=0.3, verbose=False)

    digits = []
    for r in results:
        for box in r.boxes:
            cls = int(box.cls[0])
            conf = float(box.conf[0])
            x1, y1, x2, y2 = map(int, box.xyxy[0])  # Get bounding box coordinates
            cx = (x1 + x2) / 2  # Calculate center x coordinate
            w_box = x2 - x1  # Calculate box width
            digit_label = r.names[cls]  # Get digit label from class name
            digits.append((cx, digit_label, conf, w_box))  # Add to list

    if not digits:
        return ""

    # Sort by x-coordinate (left to right)
    digits.sort(key=lambda x: x[0])

    deduped = [digits[0]]  # Start with first digit
    for i in range(1, len(digits)):  # Iterate through remaining digits
        cx_prev = deduped[-1][0]  # Get previous digit's center x
        w_prev = deduped[-1][3]  # Get previous digit's width
        cx_curr, _, conf_curr, _ = digits[i]  # Get current digit's info
        # If two detections are closer than half the digit width, they're duplicates
        if abs(cx_curr - cx_prev) < w_prev * 0.5:  # Check if overlapping
            # Keep the one with higher confidence
            if conf_curr > deduped[-1][2]:  # If current has higher confidence
                deduped[-1] = digits[i]  # Replace previous with current
        else:
            deduped.append(digits[i])  # Add as separate digit

    result = "".join(d for _, d, _, _ in deduped)  # Concatenate digits
    print(f"[OCR] Extracted digits: {result} ({len(deduped)} digits)")  # Log result
    return result  # Return extracted digits string


def detect_fields(img):

    results = object_model(img, verbose=False)  # Run object detection

    best_boxes = {}  # Dictionary to store best boxes by class name

    for r in results:
        for box in r.boxes:
            cls_name = r.names[int(box.cls[0])]  # Get class name
            conf = float(box.conf[0])  # Get confidence score
            x1, y1, x2, y2 = map(int, box.xyxy[0])  # Get bounding box coordinates

            # Store only the highest confidence box for each class
            if cls_name not in best_boxes or conf > best_boxes[cls_name][0]:
                best_boxes[cls_name] = (conf, x1, y1, x2, y2)  # Store box with confidence

    return best_boxes  # Return dictionary of best boxes


def crop_with_padding(img, box, pad_factor):
    """Crop image region with padding around the bounding box"""
    h, w = img.shape[:2]  # Get image dimensions
    _, x1, y1, x2, y2 = box  # Unpack box coordinates

    w_box, h_box = x2 - x1, y2 - y1  # Calculate box dimensions
    pad_x, pad_y = int(w_box * pad_factor), int(h_box * pad_factor)  # Calculate padding

    # Apply padding with bounds checking
    x1 = max(0, x1 - pad_x)
    y1 = max(0, y1 - pad_y)
    x2 = min(w, x2 + pad_x)
    y2 = min(h, y2 + pad_y)

    return img[y1:y2, x1:x2]


def try_extract(img, best_boxes, pad_factor, filename):
    """Uses cached detection boxes — only the crop bounds change per pad_factor,
    so no re-run of the (expensive) object model here."""
    best = ""  # Initialize best NID string

    # Process photo field if detected
    if "photo" in best_boxes:  # If photo field is detected
        crop = crop_with_padding(img, best_boxes["photo"], pad_factor)  # Crop photo with padding
        if crop is not None and crop.size != 0:  # If crop is valid
            os.makedirs("static", exist_ok=True)
            cv2.imwrite(f"static/{filename}", crop)

    # Process NID field if detected
    if "nid" in best_boxes:  # If NID field is detected
        crop = crop_with_padding(img, best_boxes["nid"], pad_factor)  # Crop NID with padding
        nid = detect_digits(crop)  # Extract digits from NID crop

        if validate_nid(nid):  # If valid 14-digit NID found
            return nid  # Return it immediately

        # Bug fix: only keep `nid` as "best" if it's actually a better (longer)
        # candidate than what we already have.
        if len(nid) > len(best):  # If new NID is longer than previous best
            best = nid  # Update best

    return best


def process_image(img, filename):
    """Process a single image orientation through detection pipeline"""
    img = preprocess_for_yolo(img)  # Preprocess image for YOLO

    if DEBUG:  # If debug mode is enabled
        cv2.imwrite("debug_full.jpg", img)  # Save debug image

    # Object detection runs exactly once per image now.
    best_boxes = detect_fields(img)  # Detect fields in image

    best = ""  # Initialize best NID string
    # Try different padding factors to improve detection
    for pad_factor in (0.20, 0.30, 0.40):  # Try increasing padding
        nid = try_extract(img, best_boxes, pad_factor, filename)  # Try to extract NID

        if DEBUG:  # If debug mode is enabled
            print(f"try pad {pad_factor}: {nid}")  # Log attempt

        if validate_nid(nid):
            return {"nid": nid}

        if len(nid) > len(best):  # If this attempt found a longer NID
            best = nid

    return {"nid": best}


def detect_and_process(img, filename):
    """Try all image rotations to handle different orientations"""
    # Create list of image rotations (0, 90, 180, 270 degrees)
    rotations = [
        img,  # Original orientation
        cv2.rotate(img, cv2.ROTATE_90_CLOCKWISE),  # 90° clockwise
        cv2.rotate(img, cv2.ROTATE_180),  # 180°
        cv2.rotate(img, cv2.ROTATE_90_COUNTERCLOCKWISE),  # 90° counter-clockwise
    ]

    best = ""  # Initialize best NID string
    for r in rotations:  # Try each rotation
        result = process_image(r, filename)  # Process rotated image
        nid = result["nid"]  # Get extracted NID

        if DEBUG:
            print("result:", nid)

        if validate_nid(nid):
            return nid

        if len(nid) > len(best):  # If this rotation found a longer NID
            best = nid  # Update best

    return best


@app.post("/extract-nid")  # Define API endpoint for NID extraction
async def extract_nid(file: UploadFile = File(...)):  # Accept file upload
    image_bytes = await file.read()  # Read uploaded file bytes

    try:
        img = read_image(image_bytes)  # Convert bytes to image
    except ValueError:  # If image decoding fails
        return {"error": "invalid image"}  # Return error response

    filename = safe_filename(file.filename)  # Sanitize filename

    # Offload the blocking YOLO/OpenCV work to the thread pool so the event
    # loop stays free to serve other requests concurrently.
    loop = asyncio.get_event_loop()
    nid = await loop.run_in_executor(_executor, detect_and_process, img, filename)  # Run blocking operation in thread pool

    return {
        "nid": nid,
        "valid": validate_nid(nid),  # Whether NID is valid
        "photoUrl": f"http://127.0.0.1:8000/static/{filename}",  # URL to saved photo
    }


@app.post("/face-match")
async def face_match_endpoint(
    id_image: UploadFile = File(...),
    selfie: UploadFile = File(...),
):
    id_bytes = await id_image.read()  # Read ID image bytes
    selfie_bytes = await selfie.read()  # Read selfie bytes

    img1 = read_image(id_bytes)  # Convert ID image bytes to image
    img2 = read_image(selfie_bytes)  # Convert selfie bytes to image

    loop = asyncio.get_event_loop()
    result = await loop.run_in_executor(  # Run blocking operation in thread pool
        _executor, face_match, img1, img2, object_model  # Call face_match function
    )
    return result