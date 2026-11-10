from functools import lru_cache
from io import BytesIO
from threading import Lock
from zipfile import BadZipFile, ZipFile

import numpy as np
from fastapi import FastAPI, File, HTTPException, UploadFile
from openpyxl import load_workbook
from PIL import Image, ImageOps, UnidentifiedImageError

app = FastAPI()
ocr_lock = Lock()
MAX_BYTES = 8 * 1024 * 1024
Image.MAX_IMAGE_PIXELS = 20_000_000


@lru_cache(maxsize=1)
def get_ocr():
    from paddleocr import PaddleOCR
    return PaddleOCR(lang="korean", device="cpu", use_doc_orientation_classify=False,
                     use_doc_unwarping=False, use_textline_orientation=False)


@app.post('/extract')
async def extract(file: UploadFile = File(...)):
    content = await file.read(MAX_BYTES + 1)
    if len(content) > MAX_BYTES:
        raise HTTPException(413, '파일은 8MB 이하여야 합니다.')
    if file.filename and file.filename.lower().endswith('.xlsx'):
        try:
            with ZipFile(BytesIO(content)) as archive:
                if sum(entry.file_size for entry in archive.infolist()) > 40 * 1024 * 1024:
                    raise HTTPException(413, '엑셀 파일의 압축 해제 크기가 너무 큽니다.')
            workbook = load_workbook(BytesIO(content), read_only=True, data_only=True)
            try:
                sheet = workbook.active
                if sheet.max_row > 1000 or sheet.max_column > 50:
                    raise HTTPException(400, '엑셀은 1000행, 50열 이하만 지원합니다.')
                rows = [[str(value).strip() if value is not None else '' for value in row]
                        for row in sheet.iter_rows(values_only=True)]
                return {'kind': 'xlsx', 'rows': rows}
            finally:
                workbook.close()
        except (BadZipFile, ValueError, KeyError, OSError):
            raise HTTPException(400, '엑셀 파일을 읽을 수 없습니다.')
    try:
        with Image.open(BytesIO(content)) as image:
            if image.format not in ('JPEG', 'PNG', 'WEBP'):
                raise HTTPException(400, 'PNG, JPEG, WebP 이미지만 지원합니다.')
            array = np.array(ImageOps.exif_transpose(image).convert('RGB'))[:, :, ::-1].copy()
    except (UnidentifiedImageError, OSError, Image.DecompressionBombError):
        raise HTTPException(400, '이미지를 읽을 수 없거나 이미지가 너무 큽니다.')
    with ocr_lock:
        result = get_ocr().predict(array)
    texts = [text.strip() for page in result for text in page.get('rec_texts', []) if text.strip()]
    return {'kind': 'image', 'texts': texts[:300]}
