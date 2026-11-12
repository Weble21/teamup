FROM python:3.11-slim
WORKDIR /app
COPY imports/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY imports/app.py .
COPY imports/test_app.py .
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
