from deepface import DeepFace
import cv2
import numpy as np


def read_image(file_bytes):
    np_arr = np.frombuffer(file_bytes, np.uint8)
    img = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)
    return img


def face_match(img1, img2):

    try:
        result = DeepFace.verify(
            img1_path=img1,
            img2_path=img2,
            model_name="ArcFace",
            detector_backend="retinaface",
            enforce_detection=False
        )

        distance = float(result["distance"])
        threshold = float(result["threshold"])
        is_matched = distance < threshold

        return {
            "match": is_matched,
            "distance": distance,
            "threshold": threshold
        }

    except Exception as e:
        return {
            "match": False,
            "error": str(e)
        }