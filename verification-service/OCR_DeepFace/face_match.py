from deepface import DeepFace
import cv2
import numpy as np


def read_image(file_bytes):
    np_arr = np.frombuffer(file_bytes, np.uint8)
    img = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)
    return img


def detect_face_exists(img):
    """
    Check whether a face can actually be detected in the image
    using RetinaFace. Returns True only if at least one face is found.
    This prevents non-face images (blank photos, gradients, etc.)
    from being accepted.
    """
    try:
        faces = DeepFace.extract_faces(
            img_path=img,
            detector_backend="retinaface",
            enforce_detection=True
        )
        return len(faces) > 0
    except Exception as e:
        print(f"  face detection check failed: {str(e)}")
        return False


def extract_face_from_id(id_img, object_model):
    """
    Use the YOLO object model to crop the 'photo' region from
    the national ID card. Returns the cropped face image, or
    None if no photo region is detected.
    """
    results = object_model(id_img, verbose=False)
    for r in results:
        for box in r.boxes:
            cls_name = r.names[int(box.cls[0])]
            if cls_name == "photo":
                x1, y1, x2, y2 = map(int, box.xyxy[0])
                h, w = id_img.shape[:2]
                # Add small padding around the detected photo
                pad_x = int((x2 - x1) * 0.05)
                pad_y = int((y2 - y1) * 0.05)
                x1 = max(0, x1 - pad_x)
                y1 = max(0, y1 - pad_y)
                x2 = min(w, x2 + pad_x)
                y2 = min(h, y2 + pad_y)
                crop = id_img[y1:y2, x1:x2]
                if crop is not None and crop.size != 0:
                    return crop
    return None


def face_match(img1, img2, object_model=None):
    """
    Compare the face on the national ID (img1) against the selfie (img2).

    Steps:
    1. Pre-validate that the selfie contains a real, detectable face.
    2. Try all 4 rotations of the ID image.
    3. For each rotation, try to extract the face photo region using YOLO.
    4. Compare the extracted face (or full ID if extraction fails) to the selfie.

    Returns a dict with key "match" (bool) to align with the Java
    FaceMatchResponse DTO.
    """

    # ── Pre-check: reject selfies with no detectable face ──
    if not detect_face_exists(img2):
        print("REJECTED: No face detected in the selfie image")
        return {
            "match": False,
            "distance": 0.0,
            "threshold": 0.0,
            "error": "No face detected in the selfie image"
        }

    id_rotations = [
        img1,
        cv2.rotate(img1, cv2.ROTATE_90_CLOCKWISE),
        cv2.rotate(img1, cv2.ROTATE_180),
        cv2.rotate(img1, cv2.ROTATE_90_COUNTERCLOCKWISE)
    ]

    best_distance = float("inf")
    best_result = None

    for i, id_img in enumerate(id_rotations):

        # Try to extract just the face photo from the ID card
        face_crop = None
        if object_model is not None:
            face_crop = extract_face_from_id(id_img, object_model)

        # Use the extracted face if available, otherwise fall back to full image
        compare_img = face_crop if face_crop is not None else id_img

        if face_crop is not None:
            print(f"rotation {i}: using YOLO-extracted face crop ({face_crop.shape})")
        else:
            print(f"rotation {i}: no face crop detected, using full ID image")

        try:
            result = DeepFace.verify(
                img1_path=compare_img,
                img2_path=img2,
                model_name="VGG-Face",
                enforce_detection=True,
                detector_backend="retinaface"
            )

            distance = float(result.get("distance", 999))

            print(
                f"  id_rotation={i} "
                f"distance={distance} "
                f"verified={result.get('verified')}"
            )

            if result.get("verified"):
                return {
                    "match": True,
                    "distance": round(distance, 4),
                    "threshold": result.get("threshold"),
                }

            if distance < best_distance:
                best_distance = distance
                best_result = result

        except ValueError as e:
            # DeepFace raises ValueError when enforce_detection=True
            # and no face is found in the image
            print(f"  rotation {i}: no face detected — {str(e)}")
        except Exception as e:
            print(f"  rotation {i} error: {str(e)}")

    if best_result is None:
        return {
            "match": False,
            "distance": 0.0,
            "threshold": 0.0,
            "error": "Face comparison failed — no face detected in either image"
        }

    return {
        "match": False,
        "distance": round(best_distance, 4),
        "threshold": best_result.get("threshold"),
    }