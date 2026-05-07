import cv2

def take_selfie(output_path="selfie.jpg"):
    cap = cv2.VideoCapture(0)

    if not cap.isOpened():
        print("Camera not found!")
        return

    print("Press SPACE to capture image...")

    while True:
        ret, frame = cap.read()
        if not ret:
            break

        cv2.imshow("Selfie Camera", frame)

        key = cv2.waitKey(1)

        # SPACE = capture
        if key == 32:
            cv2.imwrite(output_path, frame)
            print("Saved:", output_path)
            break

        # ESC = exit
        if key == 27:
            break

    cap.release()
    cv2.destroyAllWindows()

take_selfie()