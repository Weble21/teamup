import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import { createRequire } from 'node:module';
import vm from 'node:vm';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';

// Use the project's existing JSX compiler; this does not launch or control a browser.
const filename = new URL('../app/MemberImport.js', import.meta.url);
const require = createRequire(filename);
const { loadBindings, transform } = require('next/dist/build/swc');
await loadBindings();
const compiled = await transform(await fs.readFile(filename, 'utf8'), {
  filename: filename.pathname,
  jsc: { parser: { syntax: 'ecmascript', jsx: true }, transform: { react: { runtime: 'automatic' } } },
  module: { type: 'commonjs' },
});
const evaluatedModule = { exports: {} };
vm.runInNewContext(compiled.code, { module: evaluatedModule, exports: evaluatedModule.exports, require });
const MemberImport = evaluatedModule.exports.default;

for (const busy of [false, true]) {
  test(`페이지 전체 busy=${busy}여도 Excel 파일 선택과 읽기 버튼은 활성화된다`, () => {
    const html = renderToStaticMarkup(React.createElement(MemberImport, {
      busy, onClose() {}, onSave() {}, sportType: 'BASKETBALL',
    }));
    const file = html.match(/<input\b[^>]*type="file"[^>]*>/)?.[0];
    const read = html.match(/<button\b[^>]*>파일에서 읽기<\/button>/)?.[0];
    assert.ok(file); assert.ok(read);
    assert.doesNotMatch(file, /\bdisabled\b/);
    assert.doesNotMatch(read, /\bdisabled\b/);
    const fieldset = html.indexOf('<fieldset');
    assert.ok(fieldset < 0 || html.indexOf(file) < fieldset);
    assert.ok(fieldset < 0 || html.indexOf(read) < fieldset);
  });
}
