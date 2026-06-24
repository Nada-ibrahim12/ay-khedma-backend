from deepface import DeepFace
import cv2
import numpy as np

def read_image(file_bytes):
    np_arr = np.frombuffer(file_bytes, np.uint8)
    img = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)
    return img

def face_match(img1, img2):

    id_rotations = [
        img1,
        cv2.rotate(img1, cv2.ROTATE_90_CLOCKWISE),
        cv2.rotate(img1, cv2.ROTATE_180),
        cv2.rotate(img1, cv2.ROTATE_90_COUNTERCLOCKWISE)
    ]

    best_distance = float("inf")
    best_result = None
    best_id_rotation = 0

    for i, id_img in enumerate(id_rotations):

        try:

            result = DeepFace.verify(
                img1_path=id_img,
                img2_path=img2,
                enforce_detection=False,
                detector_backend="opencv"
            )

            distance = float(
                result.get("distance", 999)
            )

            print(
                f"id_rotation={i} "
                f"distance={distance} "
                f"verified={result.get('verified')}"
            )

            if result.get("verified"):
                return {
                    "verified": True,
                    "distance": round(distance, 4),
                    "threshold": result.get("threshold"),
                    # "model": result.get("model"),
                    # "similarity_percent": round((1 - distance) * 100, 2),
                    # "best_id_rotation": i
                }

            if distance < best_distance:
                best_distance = distance
                best_result = result
                best_id_rotation = i

        except Exception as e:
            print(
                f"rotation {i} error:",
                str(e)
            )

    if best_result is None:
        return {
            "verified": False,
            "distance": None,
            "message": "Face comparison failed"
        }

    similarity = max(
        0,
        round((1 - best_distance) * 100, 2)
    )

    return {
        "verified": False,
        "distance": round(best_distance, 4),
        "threshold": best_result.get("threshold"),

    }