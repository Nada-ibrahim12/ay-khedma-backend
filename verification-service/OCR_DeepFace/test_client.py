import requests
import time

url = "http://127.0.0.1:8000/extract-nid"
image_path = "dataset/n.jpg"

max_retries = 20  
retry_delay = 3  

for attempt in range(max_retries):
    try:
        print(f"Attempting to connect to server... (attempt {attempt + 1}/{max_retries})")
        files = {
            "file": open(image_path, "rb")
        }
        response = requests.post(url, files=files, timeout=60)
        
        print("Status Code:", response.status_code)
        print("Response:", response.json())
        break
        
    except (requests.exceptions.ConnectionError, requests.exceptions.Timeout):
        if attempt < max_retries - 1:
            print(f"Server not ready or timeout, waiting {retry_delay}s...")
            time.sleep(retry_delay)
        else:
            print("Failed to connect to server after multiple retries.")
            raise
    except Exception as e:
        print(f"Error: {e}")
        raise


url = "http://127.0.0.1:8000/face-match"

files = {
    "id_image": open("dataset/n.jpg", "rb"),
    "selfie": open("selfie.jpg", "rb")
}

response = requests.post(url, files=files)

print("Status:", response.status_code)
print("Response:", response.json())