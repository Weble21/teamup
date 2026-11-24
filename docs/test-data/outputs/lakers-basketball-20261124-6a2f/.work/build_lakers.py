from pathlib import Path
import re
import sys
import json
import zipfile
import xml.etree.ElementTree as ET

OUT = Path(__file__).resolve().parent.parent
ROOT = Path(__file__).resolve().parents[3]
TARGET = OUT / 'lakers-basketball-test.xlsx'


def build():
    from openpyxl import Workbook, load_workbook
    from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
    from openpyxl.worksheet.table import Table, TableStyleInfo
    from openpyxl.worksheet.datavalidation import DataValidation
    from openpyxl.formatting.rule import CellIsRule

    fixture = (ROOT / 'src/test/kotlin/com/teamup/teamup/model/service/LakersBasketballFixture.kt').read_text(encoding='utf-8')
    members = re.findall(r'NewClubMember\("([^"]+)", "([^"]+)", (\d+), null, null\)', fixture)
    attendance_block = fixture.split('val attendingNames = setOf(', 1)[1]
    attendees = set(re.findall(r'"([^"]+)"', attendance_block))
    assert len(members) == 21 and len(attendees) == 14
    assert len({name for name, _, _ in members}) == 21
    assert attendees <= {name for name, _, _ in members}
    rows = [[name, position, int(rating) // 10, '참석' if name in attendees else '불참'] for name, position, rating in members]
    assert all(int(rating) % 10 == 0 and 1 <= int(rating) // 10 <= 10 for _, _, rating in members)
    assert sum(int(rating) for name, _, rating in members if name in attendees) == 1110

    wb = Workbook()
    ws = wb.active
    ws.title = '회원목록'
    ws.sheet_view.showGridLines = False
    ws.freeze_panes = 'A2'
    ws.append(['이름', '포지션', '능력치 (1~10)', '참석 여부'])
    for row in rows:
        ws.append(row)

    table = Table(displayName='LakersMembers', ref='A1:D22')
    table.tableStyleInfo = TableStyleInfo(name='TableStyleMedium2', showRowStripes=True, showColumnStripes=False)
    ws.add_table(table)
    navy = '122446'
    muted = '5D6D83'
    for row in ws.iter_rows(min_row=1, max_row=22, min_col=1, max_col=4):
        for cell in row:
            cell.font = Font(name='맑은 고딕', size=11, color='18243A')
            cell.alignment = Alignment(vertical='center', horizontal='right' if cell.column == 3 else 'left')
            if cell.row > 1:
                cell.fill = PatternFill('solid', fgColor='F1F5FA' if cell.row % 2 == 0 else 'FFFFFF')
        ws.row_dimensions[row[0].row].height = 23
    ws.row_dimensions[1].height = 28
    for cell in ws[1][:4]:
        cell.font = Font(name='맑은 고딕', size=11, bold=True, color='FFFFFF')
        cell.fill = PatternFill('solid', fgColor=navy)
        cell.alignment = Alignment(horizontal='center', vertical='center')
        cell.border = Border(right=Side(style='thin', color='FFFFFF'))
    for key, width in {'A':36, 'B':12, 'C':20, 'D':14, 'E':4, 'F':20, 'G':82}.items():
        ws.column_dimensions[key].width = width
    for cell in ws['C'][1:22]:
        cell.number_format = '0'
    # Arial covers the Latin accents in the source names; Korean labels use Malgun Gothic.
    for cell in ws['A'][1:22]:
        cell.font = Font(name='Arial', size=11, color='18243A')

    for column, formula in [('B', '"PG,SG,SF,PF,C"'), ('D', '"참석,불참"')]:
        validation = DataValidation(type='list', formula1=formula, allow_blank=False)
        validation.errorTitle = '입력값 확인'
        validation.error = '목록에서 값을 선택해 주세요.'
        validation.showErrorMessage = True
        validation.errorStyle = 'stop'
        ws.add_data_validation(validation)
        validation.add(f'{column}2:{column}22')
    rating_validation = DataValidation(type='whole', operator='between', formula1=1, formula2=10, allow_blank=False)
    rating_validation.showErrorMessage = True
    rating_validation.errorTitle = '능력치 확인'
    rating_validation.error = '1~10의 정수를 입력해 주세요.'
    rating_validation.errorStyle = 'stop'
    ws.add_data_validation(rating_validation)
    rating_validation.add('C2:C22')
    ws.conditional_formatting.add('D2:D22', CellIsRule(operator='equal', formula=['"참석"'], fill=PatternFill('solid', fgColor='E6F1E9'), font=Font(name='맑은 고딕', size=11, color='22643A')))

    ws['F1'] = '농구 테스트'
    ws['F1'].font = Font(name='맑은 고딕', size=15, bold=True, color=navy)
    notes = {
        3: ('모임 이름', 'test'),
        4: ('종목', '농구 (BASKETBALL)'),
        5: ('팀 수', 2),
        7: ('업로드 방법', '이름은 1번째 열, 첫 행은 제목으로 선택'),
        8: ('포지션·능력치', '현재 앱은 이름만 읽으므로 등록 화면에서 직접 입력'),
        9: ('참석자 선택', '회원 등록 후 참석으로 표시된 회원을 선택'),
        11: ('값의 의미', '포지션과 능력치는 실제 평가가 아닌 임의 테스트 값'),
        12: ('명단 범위', '현재 레이커스 선수 명단을 뜻하지 않는 테스트용 이름 21개'),
        14: ('출처', 'docs/lakers-basketball-test.md'),
        15: ('상세 데이터', 'LakersBasketballFixture.kt'),
    }
    for row, (label, value) in notes.items():
        ws.cell(row, 6, label)
        ws.cell(row, 7, value)
        ws.cell(row, 6).font = Font(name='맑은 고딕', size=11, bold=True, color=navy)
        ws.cell(row, 7).font = Font(name='맑은 고딕', size=11, color=muted)
        for col in (6, 7):
            ws.cell(row, col).alignment = Alignment(vertical='center', horizontal='left')
    ws.sheet_properties.pageSetUpPr.fitToPage = True
    ws.page_setup.orientation = 'landscape'
    ws.page_setup.paperSize = ws.PAPERSIZE_A3
    ws.page_setup.fitToWidth = 1
    ws.page_setup.fitToHeight = 1
    ws.print_area = 'A1:G22'
    wb.save(TARGET)

    actual = load_workbook(TARGET, data_only=True)
    sheet = actual.active
    assert sheet.title == '회원목록'
    assert list(sheet.iter_rows(min_row=2, max_row=22, min_col=1, max_col=4, values_only=True)) == [tuple(row) for row in rows]
    assert sum(sheet.cell(i, 4).value == '참석' for i in range(2, 23)) == 14
    assert sum(sheet.cell(i, 4).value == '불참' for i in range(2, 23)) == 7
    assert all(isinstance(sheet.cell(i, 3).value, int) for i in range(2, 23))
    assert len(sheet.data_validations.dataValidation) == 3
    assert 'LakersMembers' in sheet.tables
    assert sheet.freeze_panes == 'A2'
    assert sheet['A19'].value == 'Luka Dončić'
    assert sheet['A14'].value == 'Chris Mañon'
    actual.close()
    print(json.dumps({'workbook': str(TARGET), 'members': len(rows), 'attending': 14, 'absent': 7, 'verified': True}, ensure_ascii=False))


def render():
    from PIL import Image, ImageDraw, ImageFont
    ns = {'s': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
    with zipfile.ZipFile(TARGET) as z:
        root = ET.fromstring(z.read('xl/worksheets/sheet1.xml'))
    cells = {}
    for cell in root.findall('.//s:sheetData/s:row/s:c', ns):
        text = ''.join(t.text or '' for t in cell.findall('.//s:t', ns))
        value = cell.find('s:v', ns)
        cells[cell.attrib['r']] = text if cell.attrib.get('t') == 'inlineStr' else (value.text if value is not None else '')
    font = ImageFont.truetype('C:/Windows/Fonts/malgun.ttf', 15)
    bold = ImageFont.truetype('C:/Windows/Fonts/malgunbd.ttf', 15)
    title = ImageFont.truetype('C:/Windows/Fonts/malgunbd.ttf', 19)
    names_font = ImageFont.truetype('C:/Windows/Fonts/arial.ttf', 15)
    widths = {'A':257, 'B':89, 'C':145, 'D':103, 'F':145, 'G':579}
    for filename, columns, count in [('members-preview.png', list('ABCD'), 22), ('guide-preview.png', list('FG'), 15)]:
        image = Image.new('RGB', (sum(widths[c] for c in columns) + 40, count * 32 + 40), 'white')
        draw = ImageDraw.Draw(image)
        for row in range(1, count + 1):
            x = 20
            for col in columns:
                text = cells.get(f'{col}{row}', '')
                y = 20 + (row - 1) * 32
                header = col in 'ABCD' and row == 1
                fill = '#122446' if header else '#F1F5FA' if col in 'ABCD' and row % 2 == 0 else 'white'
                if col == 'D' and text == '참석':
                    fill = '#E6F1E9'
                draw.rectangle((x, y, x + widths[col], y + 32), fill=fill)
                face = title if col == 'F' and row == 1 else bold if header or col == 'F' else font
                if col == 'A' and row > 1:
                    face = names_font
                color = 'white' if header else '#18243A'
                size = draw.textlength(str(text), font=face)
                assert size <= widths[col] - 16, f'Text clipped: {col}{row}: {text}'
                tx = x + (widths[col] - size) / 2 if header else x + widths[col] - size - 8 if col == 'C' else x + 8
                draw.text((tx, y + 5), str(text), font=face, fill=color)
                x += widths[col]
        image.save(OUT / '.work' / filename)
    print('Saved workbook data and guide previews; all text fits.')


if __name__ == '__main__':
    render() if '--render' in sys.argv else build()
