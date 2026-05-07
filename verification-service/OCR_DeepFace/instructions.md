

## 🧱 Project Structure

```
National_ID_AI/
│
├── main.py              # FastAPI endpoints
├── model.py             # OCR + YOLO processing
├── face_match.py        # Face verification logic
├── weights/
│   ├── detect_id.pt
│   └── detect_odjects.pt
│
├── test_client.py       # API testing
└── requirements.txt
```

---

## ⚙️ Installation

### 1. Install dependencies

```bash
pip install fastapi uvicorn opencv-python numpy easyocr ultralytics deepface requests
```

---

## ▶️ Run the Server

```bash
python -m uvicorn main:app --reload --port 8000   
```

📍 at same time run:

```
python test_client.py      
```

```
1- run file capture_face to take selfie
2- ensure photos name and dir in test_client
3- run: python -m uvicorn main:app --reload --port 8000    
4- python test_client.py  
```