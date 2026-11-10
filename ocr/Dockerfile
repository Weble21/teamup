FROM python:3.11-slim
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends libgomp1 libgl1 libglib2.0-0 && rm -rf /var/lib/apt/lists/*
COPY ocr/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY ocr/app.py .
ENV PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
