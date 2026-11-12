from io import BytesIO
from zipfile import BadZipFile, ZipFile

from fastapi import FastAPI, File, HTTPException, UploadFile
from openpyxl import load_workbook

app = FastAPI()
MAX_BYTES = 8 * 1024 * 1024
MAX_UNCOMPRESSED_BYTES = 40 * 1024 * 1024


@app.post('/extract')
async def extract(file: UploadFile = File(...)):
    if not file.filename or not file.filename.lower().endswith('.xlsx'):
        raise HTTPException(400, 'Excel(.xlsx) 파일만 지원합니다.')

    content = await file.read(MAX_BYTES + 1)
    if len(content) > MAX_BYTES:
        raise HTTPException(413, '파일은 8MB 이하여야 합니다.')

    try:
        with ZipFile(BytesIO(content)) as archive:
            if sum(entry.file_size for entry in archive.infolist()) > MAX_UNCOMPRESSED_BYTES:
                raise HTTPException(413, '압축 해제 크기가 너무 큽니다.')
        workbook = load_workbook(BytesIO(content), read_only=True, data_only=True)
        try:
            sheet = workbook.active
            if sheet.max_row > 1000 or sheet.max_column > 50:
                raise HTTPException(400, '첫 시트는 1000행·50열 이하만 지원합니다.')
            rows = [[str(value).strip() if value is not None else '' for value in row]
                    for row in sheet.iter_rows(values_only=True)]
            return {'kind': 'xlsx', 'rows': rows}
        finally:
            workbook.close()
    except (BadZipFile, ValueError, KeyError, OSError):
        raise HTTPException(400, 'Excel 파일을 읽을 수 없습니다.')
