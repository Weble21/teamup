FROM python:3.11-slim
WORKDIR /app
COPY constraints/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
ENV FASTEMBED_CACHE_PATH=/models
RUN python -c "from fastembed import TextEmbedding; TextEmbedding(model_name='sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2', cache_dir='/models')"
COPY constraints/app.py .
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8001"]
