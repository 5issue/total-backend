/**
 * bin/sync-notion-db.js와 bin/sync-notion-events.js가 공유하는 유틸리티.
 * - TypeSpec이 생성한 OpenAPI(YAML) 스키마 파싱/예시 생성
 * - 노션 블록 빌더
 * - 노션 API 호출(재시도, data source 해석, 페이지 upsert 공통 흐름)
 */

import fs from 'node:fs';
import { load as loadYaml } from 'js-yaml';
import { isNotionClientError, APIErrorCode } from '@notionhq/client';

export function fail(message) {
  console.error(`✖ ${message}`);
  process.exit(1);
}

export function loadSpec(specPath) {
  if (!fs.existsSync(specPath)) {
    fail(`파일이 존재하지 않습니다: ${specPath}`);
  }
  const raw = fs.readFileSync(specPath, 'utf8');
  return loadYaml(raw);
}

// ── OpenAPI 스키마 유틸 ──────────────────────────────────────────────────
// TypeSpec의 openapi3 emitter는 `T | null`을 버전에 따라 다르게 표현한다:
//   3.0: { allOf: [{ $ref }], nullable: true }  또는  { type, nullable: true }
//   3.1: { anyOf: [{ $ref } | {...}, { type: 'null' }] }
// unwrapNullable은 두 형태 모두에서 "null이 아닌 쪽" 스키마 노드를 꺼낸다.

export function refName(ref) {
  return ref.replace('#/components/schemas/', '');
}

export function unwrapNullable(schema) {
  if (!schema) return schema;
  if (Array.isArray(schema.allOf)) {
    const withRef = schema.allOf.find((s) => s.$ref);
    if (withRef) return withRef;
  }
  if (Array.isArray(schema.anyOf)) {
    const notNull = schema.anyOf.find((s) => s.type !== 'null');
    if (notNull) return notNull;
  }
  return schema;
}

export function resolveSchema(doc, schema) {
  const unwrapped = unwrapNullable(schema);
  if (unwrapped?.$ref) {
    return doc.components.schemas[refName(unwrapped.$ref)];
  }
  return unwrapped;
}

export function describeType(doc, schema, depth = 0) {
  if (!schema || depth > 5) return 'unknown';
  const unwrapped = unwrapNullable(schema);

  if (unwrapped.$ref) {
    const name = refName(unwrapped.$ref);
    const target = doc.components.schemas[name];
    if (target?.enum) return `${name} (enum: ${target.enum.join('|')})`;
    return name;
  }
  if (unwrapped.type === 'array') {
    return `array<${describeType(doc, unwrapped.items, depth + 1)}>`;
  }
  if (unwrapped.enum) {
    return `enum: ${unwrapped.enum.join('|')}`;
  }
  if (unwrapped.type === 'integer' || unwrapped.type === 'number') {
    return unwrapped.format ? `${unwrapped.type}(${unwrapped.format})` : unwrapped.type;
  }
  if (unwrapped.type === 'string') {
    return unwrapped.format ? `string(${unwrapped.format})` : 'string';
  }
  return unwrapped.type || 'object';
}

export function generateExample(doc, schema, { seen = new Set(), depth = 0, keyHint = '' } = {}) {
  if (!schema || depth > 8) return null;
  const unwrapped = unwrapNullable(schema);

  let resolved = unwrapped;
  let name = null;
  if (unwrapped.$ref) {
    name = refName(unwrapped.$ref);
    if (seen.has(name)) return {}; // 순환 참조 가드
    resolved = doc.components.schemas[name];
    seen = new Set(seen).add(name);
  }
  if (!resolved) return null;

  if (resolved.enum) return resolved.enum[0];

  if (resolved.type === 'array') {
    return [generateExample(doc, resolved.items, { seen, depth: depth + 1, keyHint })];
  }
  if (resolved.type === 'object' || resolved.properties) {
    const obj = {};
    for (const [key, propSchema] of Object.entries(resolved.properties || {})) {
      obj[key] = generateExample(doc, propSchema, { seen, depth: depth + 1, keyHint: key });
    }
    return obj;
  }
  switch (resolved.type) {
    case 'integer':
    case 'number':
      if (keyHint === 'id' || /Id$/.test(keyHint)) return 1;
      if (/quantity/i.test(keyHint)) return 10;
      return 0;
    case 'boolean':
      return true;
    case 'string':
      if (resolved.format === 'date-time') return '2026-09-14T00:00:00Z';
      if (resolved.format === 'date') return '2026-09-14';
      return 'string';
    default:
      return null;
  }
}

// ── 노션 블록 빌더 ─────────────────────────────────────────────────────

const RICH_TEXT_LIMIT = 2000;

function chunkText(text) {
  const chunks = [];
  for (let i = 0; i < text.length; i += RICH_TEXT_LIMIT) {
    chunks.push(text.slice(i, i + RICH_TEXT_LIMIT));
  }
  return chunks.length ? chunks : [''];
}

export function rt(text) {
  return chunkText(String(text ?? '')).map((chunk) => ({ type: 'text', text: { content: chunk } }));
}

export function heading(text, level = 2) {
  const type = level === 2 ? 'heading_2' : 'heading_3';
  return { object: 'block', type, [type]: { rich_text: rt(text) } };
}

export function paragraph(text) {
  return { object: 'block', type: 'paragraph', paragraph: { rich_text: rt(text) } };
}

export function callout(text, emoji = '📌') {
  return { object: 'block', type: 'callout', callout: { rich_text: rt(text), icon: { type: 'emoji', emoji } } };
}

export function bulletedListItem(text) {
  return { object: 'block', type: 'bulleted_list_item', bulleted_list_item: { rich_text: rt(text) } };
}

export function codeBlock(value, language = 'json') {
  const content = typeof value === 'string' ? value : JSON.stringify(value, null, 2);
  return { object: 'block', type: 'code', code: { language, rich_text: rt(content) } };
}

export function tableRow(cells) {
  return { object: 'block', type: 'table_row', table_row: { cells: cells.map((c) => rt(c)) } };
}

export function table(headers, rows) {
  return {
    object: 'block',
    type: 'table',
    table: {
      table_width: headers.length,
      has_column_header: true,
      has_row_header: false,
      children: [tableRow(headers), ...rows.map(tableRow)],
    },
  };
}

// ── 노션 API 연동 ─────────────────────────────────────────────────────

export function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export async function withRetry(fn, { retries = 5, label = 'Notion API 호출' } = {}) {
  for (let attempt = 1; attempt <= retries; attempt += 1) {
    try {
      return await fn();
    } catch (err) {
      const retryable =
        isNotionClientError(err) &&
        (err.code === APIErrorCode.RateLimited || err.code === APIErrorCode.InternalServerError || err.code === APIErrorCode.ServiceUnavailable);
      if (!retryable || attempt === retries) throw err;
      const waitMs = 1000 * 2 ** (attempt - 1);
      console.warn(`  ↳ ${label} 재시도 (${attempt}/${retries}) — ${waitMs}ms 대기 (${err.code})`);
      await sleep(waitMs);
    }
  }
}

// Notion API 2025-09-03부터 데이터베이스 아래에 "data source"가 생겼고, 행 조회/필터는
// database_id가 아니라 data_source_id로 한다 (databases.query는 SDK에서 아예 사라졌다).
// 이 스크립트는 단일 data source 데이터베이스만 지원한다 — 여러 개면 어느 쪽에 쓸지
// 애매해서(잘못된 data source에 쓰면 엉뚱한 곳이 갱신된다) 첫 번째를 임의로 고르는 대신 멈춘다.
export async function resolveDataSourceId(notion, databaseId) {
  const db = await withRetry(() => notion.databases.retrieve({ database_id: databaseId }), { label: '데이터베이스 조회' });
  const dataSource = db.data_sources?.[0];
  if (!dataSource) {
    fail(`데이터베이스(${databaseId})에서 data source를 찾을 수 없습니다. 대상 DATABASE_ID가 맞는지 확인하세요.`);
  }
  if (db.data_sources.length > 1) {
    fail(
      `데이터베이스(${databaseId})에 data source가 ${db.data_sources.length}개 있어 어느 쪽을 쓸지 정할 수 없습니다: ` +
        `${db.data_sources.map((ds) => `${ds.name}(${ds.id})`).join(', ')}. ` +
        `이 스크립트는 단일 data source 데이터베이스만 지원합니다 — 잘못된 data source에 쓰는 걸 막기 위해 자동으로 고르지 않습니다.`,
    );
  }
  return dataSource.id;
}

// 속성 이름이 하나라도 다르면 매 항목마다 알아보기 힘든 에러가 반복되므로,
// 루프 시작 전에 한 번에 검증해서 무엇이 문제인지 바로 알려준다.
export async function validateDatabaseSchema(notion, dataSourceId, requiredProperties) {
  const dataSource = await withRetry(() => notion.dataSources.retrieve({ data_source_id: dataSourceId }), {
    label: '데이터베이스 스키마 조회',
  });
  const missing = [];
  const wrongType = [];
  for (const [name, expectedType] of Object.entries(requiredProperties)) {
    const prop = dataSource.properties[name];
    if (!prop) {
      missing.push(name);
    } else if (prop.type !== expectedType) {
      wrongType.push(`${name} (기대: ${expectedType}, 실제: ${prop.type})`);
    }
  }
  if (missing.length || wrongType.length) {
    const lines = [];
    if (missing.length) lines.push(`누락된 속성: ${missing.join(', ')}`);
    if (wrongType.length) lines.push(`타입이 다른 속성: ${wrongType.join(', ')}`);
    fail(`노션 데이터베이스 속성이 예상과 다릅니다.\n  ${lines.join('\n  ')}`);
  }
}

export async function findExistingPage(notion, dataSourceId, filter, label) {
  const response = await withRetry(() => notion.dataSources.query({ data_source_id: dataSourceId, filter }), {
    label: `${label} 조회`,
  });
  return response.results[0] ?? null;
}

export async function listPageChildrenIds(notion, pageId) {
  let cursor;
  const blockIds = [];
  do {
    const response = await withRetry(() => notion.blocks.children.list({ block_id: pageId, start_cursor: cursor, page_size: 100 }), {
      label: '기존 블록 목록 조회',
    });
    blockIds.push(...response.results.map((b) => b.id));
    cursor = response.has_more ? response.next_cursor : undefined;
  } while (cursor);
  return blockIds;
}

export async function deleteBlocks(notion, blockIds) {
  for (const blockId of blockIds) {
    await withRetry(() => notion.blocks.delete({ block_id: blockId }), { label: '기존 블록 삭제' });
    await sleep(120);
  }
}

export async function appendChildrenInChunks(notion, pageId, children) {
  const CHUNK_SIZE = 90;
  for (let i = 0; i < children.length; i += CHUNK_SIZE) {
    const chunk = children.slice(i, i + CHUNK_SIZE);
    await withRetry(() => notion.blocks.children.append({ block_id: pageId, children: chunk }), { label: '본문 블록 추가' });
    await sleep(150);
  }
}

/**
 * find-or-create 후 속성 갱신, 본문은 통째로 비우고 다시 쓰는 공통 upsert 흐름.
 * `buildProperties(isNew)`/`buildChildren()`는 필요한 시점에만 호출되는 thunk다.
 *
 * 기존 페이지는 새 블록을 먼저 append하고 나서 옛 블록을 지운다 — 반대 순서로 하면
 * append가 중간에 실패했을 때 페이지 본문이 통째로 비는 채로 남는다.
 */
export async function upsertNotionPage(notion, dataSourceId, { filter, buildProperties, buildChildren, label }) {
  const existing = await findExistingPage(notion, dataSourceId, filter, label);
  const isNew = !existing;

  let pageId;
  let oldBlockIds = [];
  if (isNew) {
    const created = await withRetry(
      () => notion.pages.create({ parent: { data_source_id: dataSourceId }, properties: buildProperties(true) }),
      { label: `${label} 생성` },
    );
    pageId = created.id;
  } else {
    pageId = existing.id;
    await withRetry(() => notion.pages.update({ page_id: pageId, properties: buildProperties(false) }), {
      label: `${label} 속성 갱신`,
    });
    oldBlockIds = await listPageChildrenIds(notion, pageId);
  }

  await appendChildrenInChunks(notion, pageId, buildChildren());
  if (oldBlockIds.length) {
    await deleteBlocks(notion, oldBlockIds);
  }
  return isNew ? 'created' : 'updated';
}
