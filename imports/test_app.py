import asyncio
from io import BytesIO
import unittest

from fastapi import HTTPException, UploadFile
from openpyxl import Workbook

from app import extract


class ExcelImportTest(unittest.TestCase):
    def test_reads_first_sheet(self):
        workbook = Workbook()
        workbook.active.append(["이름", "포지션"])
        workbook.active.append(["김철수", "센터"])
        workbook.create_sheet("ignored").append(["다른 시트"])
        buffer = BytesIO()
        workbook.save(buffer)
        result = asyncio.run(extract(UploadFile(file=BytesIO(buffer.getvalue()), filename="members.xlsx")))
        self.assertEqual({"kind": "xlsx", "rows": [["이름", "포지션"], ["김철수", "센터"]]}, result)

    def test_rejects_images_and_invalid_excel(self):
        with self.assertRaises(HTTPException) as image_error:
            asyncio.run(extract(UploadFile(file=BytesIO(b"image"), filename="members.png")))
        self.assertEqual(400, image_error.exception.status_code)
        with self.assertRaises(HTTPException) as excel_error:
            asyncio.run(extract(UploadFile(file=BytesIO(b"not a workbook"), filename="members.xlsx")))
        self.assertEqual(400, excel_error.exception.status_code)


if __name__ == "__main__":
    unittest.main()
