export async function POST(request) {
  const origin = request.headers.get('origin');
  if (!origin || new URL(origin).host !== request.headers.get('host')) {
    return Response.json({ error: '요청 출처를 확인해 주세요.' }, { status: 403 });
  }
  if (Number(request.headers.get('content-length')) > 8 * 1024 * 1024 + 65536) {
    return Response.json({ error: '파일은 8MB 이하여야 합니다.' }, { status: 413 });
  }
  try {
    const form = await request.formData();
    const file = form.get('file');
    if (!(file instanceof File) || file.size > 8 * 1024 * 1024 || file.size === 0) {
      return Response.json({ error: '8MB 이하 파일을 선택해 주세요.' }, { status: 400 });
    }
    const forwarded = new FormData();
    forwarded.append('file', file);
    const response = await fetch(`${process.env.OCR_URL || 'http://localhost:8000'}/extract`, {
      method: 'POST', body: forwarded, signal: AbortSignal.timeout(180000),
    });
    const data = await response.json();
    return Response.json(response.ok ? data : { error: data.detail || '파일을 읽지 못했습니다.' }, { status: response.status });
  } catch {
    return Response.json({ error: '파일 처리 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.' }, { status: 503 });
  }
}
