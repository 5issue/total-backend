#!/usr/bin/env node
/**
 * TypeSpec이 생성한 OpenAPI(tsp-output/schema/{3.0.0,3.1.0}/openapi.yaml)를 파싱해서
 * 노션 데이터베이스에 API 명세를 upsert(생성/갱신)한다.
 *
 * - 매칭 키: `PATH(endpoint)` + `METHOD`. 같은 조합의 행이 있으면 갱신, 없으면 새로 만든다.
 * - `피드백/수정요청`, `검토 상태`는 절대 덮어쓰지 않는다 (업데이트 payload에 아예 포함하지 않음).
 *   `검토 상태`는 새 행을 만들 때만 기본값("🟢 정상")으로 채운다.
 * - 페이지 본문(Request/Response 블록)은 매번 통째로 비우고 새로 씁니다 — 팀원이 페이지 "속성"에
 *   적어둔 피드백/검토 상태는 보존되지만, 본문에 직접 적어둔 메모는 다음 sync에서 사라집니다.
 *
 * 사용법:
 *   cd services/wms-service/api-spec
 *   npm run sync:notion
 *   # 또는: node scripts/sync-notion-db.js [path/to/openapi.yaml]
 *
 * 드라이런(노션 호출 없이 결과만 로컬 파일로 확인):
 *   node scripts/sync-notion-db.js --dry-run
 */

import 'dotenv/config';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { load as loadYaml } from 'js-yaml';
import { Client, isNotionClientError, APIErrorCode } from '@notionhq/client';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = path.resolve(__dirname, '..');

const NOTION_TOKEN = process.env.NOTION_TOKEN;
const NOTION_DATABASE_ID = process.env.NOTION_DATABASE_ID;
const DRY_RUN = process.argv.includes('--dry-run') || process.env.DRY_RUN === 'true';

const PROTECTED_PROPERTIES = ['피드백/수정요청', '검토 상태'];
const DEFAULT_REVIEW_STATUS = '🟢 정상';
const DEFAULT_DOMAIN = 'WMS';
const DEFAULT_IMPORTANCE = '중';

// common/exception/GlobalErrorCode.java 기준. 도메인별 에러 코드는 이 스크립트가 알 수 없으므로
// 팀원이 각 페이지의 이 표를 직접 보강해야 한다 (그래서 매번 새로 그려도 되는 정보다).
const COMMON_ERROR_ROWS = [
  ['400', 'COMMON400', '잘못된 요청 파라미터/바디', '잘못된 요청입니다.'],
  ['401', 'COMMON401', '인증 토큰 없음/만료', '인증이 필요합니다.'],
  ['403', 'COMMON403', '접근 권한 없음', '접근 권한이 없습니다.'],
  ['404', 'COMMON404', '리소스를 찾을 수 없음', '요청한 리소스를 찾을 수 없습니다.'],
  ['405', 'COMMON405', '지원하지 않는 HTTP 메서드', '지원하지 않는 HTTP 메서드입니다.'],
  ['409', 'COMMON409', '요청이 현재 서버 상태와 충돌', '요청이 현재 서버 상태와 충돌합니다.'],
  ['500', 'COMMON500', '서버 내부 오류', '서버 내부 오류가 발생했습니다.'],
];

const STATUS_TEXT = {
  200: 'OK',
  201: 'Created',
  202: 'Accepted',
  204: 'No Content',
};

function fail(message) {
  console.error(`✖ ${message}`);
  process.exit(1);
}

function resolveSpecPath() {
  const cliArg = process.argv.slice(2).find((arg) => !arg.startsWith('--'));
  if (cliArg) return path.resolve(process.cwd(), cliArg);
  if (process.env.OPENAPI_SPEC_PATH) {
    return path.resolve(PROJECT_ROOT, process.env.OPENAPI_SPEC_PATH);
  }
  const candidates = [
    'tsp-output/schema/3.0.0/openapi.yaml',
    'tsp-output/schema/3.1.0/openapi.yaml',
    'tsp-output/schema/openapi.yaml',
  ];
  for (const candidate of candidates) {
    const full = path.join(PROJECT_ROOT, candidate);
    if (fs.existsSync(full)) return full;
  }
  fail(
    `OpenAPI 스펙 파일을 찾을 수 없습니다. 먼저 'npm run build'를 실행하거나, ` +
      `OPENAPI_SPEC_PATH 환경변수 / 첫 번째 인자로 경로를 지정하세요. (확인한 경로: ${candidates.join(', ')})`,
  );
}

function loadSpec(specPath) {
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

function refName(ref) {
  return ref.replace('#/components/schemas/', '');
}

function unwrapNullable(schema) {
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

function resolveSchema(doc, schema) {
  const unwrapped = unwrapNullable(schema);
  if (unwrapped?.$ref) {
    return doc.components.schemas[refName(unwrapped.$ref)];
  }
  return unwrapped;
}

function describeType(doc, schema, depth = 0) {
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

function generateExample(doc, schema, { seen = new Set(), depth = 0, keyHint = '' } = {}) {
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

// ── OpenAPI 문서 → 엔드포인트 목록 ─────────────────────────────────────────

const HTTP_METHODS = ['get', 'post', 'put', 'patch', 'delete'];

function extractEndpoints(doc) {
  const endpoints = [];
  for (const [routePath, pathItem] of Object.entries(doc.paths || {})) {
    for (const method of HTTP_METHODS) {
      const op = pathItem[method];
      if (!op) continue;

      const parameters = op.parameters || [];
      const security = op.security ?? doc.security ?? [];
      const bearer = Array.isArray(security) && security.length > 0;

      const requestBodySchema = op.requestBody?.content?.['application/json']?.schema ?? null;

      const successStatus = Object.keys(op.responses || {}).find((code) => code !== 'default') ?? '200';
      const successEnvelope = op.responses?.[successStatus]?.content?.['application/json']?.schema ?? null;
      const successDataSchema = successEnvelope?.properties?.data ?? null;

      endpoints.push({
        method: method.toUpperCase(),
        path: routePath,
        summary: op.summary || `${method.toUpperCase()} ${routePath}`,
        description: op.description || '',
        tag: op.tags?.[0] || doc.tags?.[0]?.name || 'ETC',
        bearer,
        pathParams: parameters.filter((p) => p.in === 'path'),
        queryParams: parameters.filter((p) => p.in === 'query'),
        requestBodySchema,
        successStatus,
        successDataSchema,
      });
    }
  }
  return endpoints;
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

function rt(text) {
  return chunkText(String(text ?? '')).map((chunk) => ({ type: 'text', text: { content: chunk } }));
}

function heading(text, level = 2) {
  const type = level === 2 ? 'heading_2' : 'heading_3';
  return { object: 'block', type, [type]: { rich_text: rt(text) } };
}

function paragraph(text) {
  return { object: 'block', type: 'paragraph', paragraph: { rich_text: rt(text) } };
}

function codeBlock(value) {
  const json = JSON.stringify(value, null, 2);
  return { object: 'block', type: 'code', code: { language: 'json', rich_text: rt(json) } };
}

function tableRow(cells) {
  return { object: 'block', type: 'table_row', table_row: { cells: cells.map((c) => rt(c)) } };
}

function table(headers, rows) {
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

// ── 페이지 본문 조립 ─────────────────────────────────────────────────────

function buildFieldRows(doc, schema) {
  const resolved = resolveSchema(doc, schema);
  if (!resolved?.properties) return [];
  const required = resolved.required || [];
  return Object.entries(resolved.properties).map(([field, propSchema]) => [
    field,
    describeType(doc, propSchema),
    required.includes(field) ? 'Y' : 'N',
    propSchema.description || resolveSchema(doc, propSchema)?.description || '-',
  ]);
}

function buildSuccessExample(doc, dataSchema) {
  return {
    status: 'SUCCESS',
    message: '요청에 성공하였습니다.',
    data: generateExample(doc, dataSchema),
    error: null,
    timestamp: '2026-09-14T00:00:00Z',
  };
}

function buildErrorExample() {
  return {
    status: 'ERROR',
    message: '잘못된 요청입니다.',
    data: null,
    error: 'COMMON400',
    timestamp: '2026-09-14T00:00:00Z',
  };
}

function buildPageChildren(doc, ep) {
  const blocks = [];

  blocks.push(heading('🔹 Request', 2));

  blocks.push(heading('Headers', 3));
  blocks.push(
    table(
      ['Header', 'Value'],
      [
        ['Authorization', ep.bearer ? 'Bearer {ACCESS_TOKEN}' : '(인증 불필요)'],
        ['Content-Type', ep.requestBodySchema ? 'application/json' : '-'],
      ],
    ),
  );

  if (ep.pathParams.length) {
    blocks.push(heading('Path Parameters', 3));
    blocks.push(
      table(
        ['이름', '타입', '필수', '설명'],
        ep.pathParams.map((p) => [p.name, describeType(doc, p.schema), p.required ? 'Y' : 'N', p.description || '-']),
      ),
    );
  }

  if (ep.queryParams.length) {
    blocks.push(heading('Query Parameters', 3));
    blocks.push(
      table(
        ['이름', '타입', '필수', '설명'],
        ep.queryParams.map((p) => [p.name, describeType(doc, p.schema), p.required ? 'Y' : 'N', p.description || '-']),
      ),
    );
  }

  if (ep.requestBodySchema) {
    blocks.push(heading('Body', 3));
    const fieldRows = buildFieldRows(doc, ep.requestBodySchema);
    if (fieldRows.length) {
      blocks.push(table(['필드명', '타입', '필수', '설명'], fieldRows));
    }
    blocks.push(codeBlock(generateExample(doc, ep.requestBodySchema)));
  }

  blocks.push(heading('🔹 Response', 2));

  const statusText = STATUS_TEXT[Number(ep.successStatus)];
  blocks.push(heading(`성공 (${ep.successStatus}${statusText ? ' ' + statusText : ''})`, 3));
  blocks.push(codeBlock(buildSuccessExample(doc, ep.successDataSchema)));

  blocks.push(heading('실패', 3));
  blocks.push(codeBlock(buildErrorExample()));

  blocks.push(heading('HTTP 에러 코드 정의', 3));
  blocks.push(
    paragraph('공통 에러 코드(GlobalErrorCode) 기준 기본값입니다. 이 엔드포인트만의 도메인 에러 코드는 확정되는 대로 직접 추가해주세요.'),
  );
  blocks.push(table(['HTTP 상태 코드', '에러 코드', '상황', '응답 메시지'], COMMON_ERROR_ROWS));

  return blocks;
}

// ── 노션 API 연동 ─────────────────────────────────────────────────────

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function withRetry(fn, { retries = 5, label = 'Notion API 호출' } = {}) {
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

function buildProperties(ep, { isNew }) {
  const properties = {
    이름: { title: rt(ep.summary) },
    'PATH(endpoint)': { rich_text: rt(ep.path) },
    METHOD: { select: { name: ep.method } },
    Bearer: { checkbox: ep.bearer },
    도메인: { select: { name: DEFAULT_DOMAIN } },
    상세: { select: { name: ep.tag } },
    중요도: { select: { name: DEFAULT_IMPORTANCE } },
  };
  if (isNew) {
    properties['검토 상태'] = { select: { name: DEFAULT_REVIEW_STATUS } };
  }
  return properties;
}

// Notion API 2025-09-03부터 데이터베이스 아래에 "data source"가 생겼고, 행 조회/필터는
// database_id가 아니라 data_source_id로 한다 (databases.query는 SDK에서 아예 사라졌다).
// 지금 DB가 단일 data source(거의 모든 일반 DB가 여기 해당)라고 가정하고 첫 번째 것을 사용한다.
async function resolveDataSourceId(notion, databaseId) {
  const db = await withRetry(() => notion.databases.retrieve({ database_id: databaseId }), { label: '데이터베이스 조회' });
  const dataSource = db.data_sources?.[0];
  if (!dataSource) {
    fail(`데이터베이스(${databaseId})에서 data source를 찾을 수 없습니다. NOTION_DATABASE_ID가 맞는지 확인하세요.`);
  }
  if (db.data_sources.length > 1) {
    console.warn(
      `⚠ 이 데이터베이스에 data source가 ${db.data_sources.length}개 있습니다. 첫 번째("${dataSource.name}")만 사용합니다.`,
    );
  }
  return dataSource.id;
}

const REQUIRED_PROPERTIES = {
  이름: 'title',
  'PATH(endpoint)': 'rich_text',
  METHOD: 'select',
  Bearer: 'checkbox',
  도메인: 'select',
  상세: 'select',
  중요도: 'select',
  '피드백/수정요청': 'rich_text',
  '검토 상태': 'select',
};

// 속성 이름이 하나라도 다르면 매 엔드포인트마다 알아보기 힘든 에러가 반복되므로,
// 루프 시작 전에 한 번에 검증해서 무엇이 문제인지 바로 알려준다.
async function validateDatabaseSchema(notion, dataSourceId) {
  const dataSource = await withRetry(() => notion.dataSources.retrieve({ data_source_id: dataSourceId }), {
    label: '데이터베이스 스키마 조회',
  });
  const missing = [];
  const wrongType = [];
  for (const [name, expectedType] of Object.entries(REQUIRED_PROPERTIES)) {
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

async function findExistingPage(notion, dataSourceId, ep) {
  const response = await withRetry(
    () =>
      notion.dataSources.query({
        data_source_id: dataSourceId,
        filter: {
          and: [
            { property: 'PATH(endpoint)', rich_text: { equals: ep.path } },
            { property: 'METHOD', select: { equals: ep.method } },
          ],
        },
      }),
    { label: `${ep.method} ${ep.path} 조회` },
  );
  return response.results[0] ?? null;
}

async function clearPageChildren(notion, pageId) {
  let cursor;
  const blockIds = [];
  do {
    const response = await withRetry(() => notion.blocks.children.list({ block_id: pageId, start_cursor: cursor, page_size: 100 }), {
      label: '기존 블록 목록 조회',
    });
    blockIds.push(...response.results.map((b) => b.id));
    cursor = response.has_more ? response.next_cursor : undefined;
  } while (cursor);

  for (const blockId of blockIds) {
    await withRetry(() => notion.blocks.delete({ block_id: blockId }), { label: '기존 블록 삭제' });
    await sleep(120);
  }
}

async function appendChildrenInChunks(notion, pageId, children) {
  const CHUNK_SIZE = 90;
  for (let i = 0; i < children.length; i += CHUNK_SIZE) {
    const chunk = children.slice(i, i + CHUNK_SIZE);
    await withRetry(() => notion.blocks.children.append({ block_id: pageId, children: chunk }), { label: '본문 블록 추가' });
    await sleep(150);
  }
}

async function upsertEndpoint(notion, dataSourceId, doc, ep) {
  const existing = await findExistingPage(notion, dataSourceId, ep);
  const isNew = !existing;

  let pageId;
  if (isNew) {
    const created = await withRetry(
      () =>
        notion.pages.create({
          parent: { data_source_id: dataSourceId },
          properties: buildProperties(ep, { isNew: true }),
        }),
      { label: `${ep.method} ${ep.path} 생성` },
    );
    pageId = created.id;
  } else {
    pageId = existing.id;
    await withRetry(() => notion.pages.update({ page_id: pageId, properties: buildProperties(ep, { isNew: false }) }), {
      label: `${ep.method} ${ep.path} 속성 갱신`,
    });
    await clearPageChildren(notion, pageId);
  }

  const children = buildPageChildren(doc, ep);
  await appendChildrenInChunks(notion, pageId, children);

  return isNew ? 'created' : 'updated';
}

// ── 메인 ─────────────────────────────────────────────────────────────

async function main() {
  const specPath = resolveSpecPath();
  console.log(`OpenAPI 스펙 로드: ${path.relative(PROJECT_ROOT, specPath)}`);
  const doc = loadSpec(specPath);

  const endpoints = extractEndpoints(doc);
  console.log(`엔드포인트 ${endpoints.length}개 발견\n`);

  if (DRY_RUN) {
    const preview = endpoints.map((ep) => ({
      method: ep.method,
      path: ep.path,
      properties: buildProperties(ep, { isNew: true }),
      children: buildPageChildren(doc, ep),
    }));
    const outPath = path.join(PROJECT_ROOT, 'tsp-output', 'notion-dry-run.json');
    fs.mkdirSync(path.dirname(outPath), { recursive: true });
    fs.writeFileSync(outPath, JSON.stringify(preview, null, 2));
    for (const ep of endpoints) {
      console.log(`- ${ep.method.padEnd(6)} ${ep.path}`);
    }
    console.log(`\n[dry-run] 노션 API를 호출하지 않았습니다. 생성될 내용은 여기서 확인하세요:`);
    console.log(`  ${path.relative(PROJECT_ROOT, outPath)}`);
    return;
  }

  if (!NOTION_TOKEN) fail('환경변수 NOTION_TOKEN이 설정되어 있지 않습니다 (.env 또는 GitHub Actions secret 확인).');
  if (!NOTION_DATABASE_ID) fail('환경변수 NOTION_DATABASE_ID가 설정되어 있지 않습니다 (.env 또는 GitHub Actions secret 확인).');

  const notion = new Client({ auth: NOTION_TOKEN });
  const dataSourceId = await resolveDataSourceId(notion, NOTION_DATABASE_ID);
  await validateDatabaseSchema(notion, dataSourceId);

  const results = { created: 0, updated: 0, failed: [] };
  for (const ep of endpoints) {
    process.stdout.write(`- ${ep.method.padEnd(6)} ${ep.path} ... `);
    try {
      const outcome = await upsertEndpoint(notion, dataSourceId, doc, ep);
      results[outcome] += 1;
      console.log(outcome === 'created' ? '생성됨' : '갱신됨');
    } catch (err) {
      results.failed.push({ ep, err });
      console.log(`실패 (${err.message})`);
    }
    await sleep(200);
  }

  console.log('\n--- 동기화 결과 ---');
  console.log(`생성: ${results.created}, 갱신: ${results.updated}, 실패: ${results.failed.length}`);
  if (results.failed.length) {
    console.log('\n실패한 엔드포인트:');
    for (const { ep, err } of results.failed) {
      console.log(`  - ${ep.method} ${ep.path}: ${err.message}`);
    }
    process.exitCode = 1;
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
