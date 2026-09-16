#!/usr/bin/env node
/**
 * TypeSpec이 생성한 OpenAPI(tsp-output/schema/{3.0.0,3.1.0}/openapi.yaml)를 파싱해서
 * 공용 노션 데이터베이스에 API 명세를 upsert(생성/갱신)한다. 여러 서비스가 같은 데이터베이스를
 * 공유하며 `도메인` 속성(SERVICE_DOMAIN)으로 서비스별 행을 구분한다.
 *
 * - 매칭 키: `PATH(endpoint)` + `METHOD`. 같은 조합의 행이 있으면 갱신, 없으면 새로 만든다.
 * - `피드백/수정요청`, `검토 상태`는 절대 덮어쓰지 않는다 (업데이트 payload에 아예 포함하지 않음).
 *   `검토 상태`는 새 행을 만들 때만 기본값("🟢 정상")으로 채운다.
 * - 페이지 본문(Request/Response 블록)은 매번 통째로 비우고 새로 씁니다 — 팀원이 페이지 "속성"에
 *   적어둔 피드백/검토 상태는 보존되지만, 본문에 직접 적어둔 메모는 다음 sync에서 사라집니다.
 *
 * 사용법 (각 서비스의 api-spec/ 디렉터리에서):
 *   npm run sync:notion
 *   # 또는: sync-notion-db [path/to/openapi.yaml]
 * 필수 환경변수: NOTION_TOKEN, NOTION_DATABASE_ID, SERVICE_DOMAIN(예: WMS)
 *
 * 드라이런(노션 호출 없이 결과만 로컬 파일로 확인):
 *   sync-notion-db --dry-run
 */

import 'dotenv/config';
import fs from 'node:fs';
import path from 'node:path';
import { Client } from '@notionhq/client';
import {
  fail,
  loadSpec,
  resolveSchema,
  describeType,
  generateExample,
  rt,
  heading,
  paragraph,
  codeBlock,
  table,
  sleep,
  resolveDataSourceId,
  validateDatabaseSchema,
  upsertNotionPage,
} from '../lib/notion-openapi.js';

// npm이 스크립트를 실행할 때 cwd를 그 package.json이 있는 디렉터리(각 서비스의 api-spec/)로
// 맞춰주는 것에 의존한다. 이 파일 자신의 위치(공용 패키지 설치 경로)를 기준으로 잡으면 안 된다.
const PROJECT_ROOT = process.cwd();

const NOTION_TOKEN = process.env.NOTION_TOKEN;
const NOTION_DATABASE_ID = process.env.NOTION_DATABASE_ID;
const SERVICE_DOMAIN = process.env.SERVICE_DOMAIN;
const DRY_RUN = process.argv.includes('--dry-run') || process.env.DRY_RUN === 'true';

const DEFAULT_REVIEW_STATUS = '🟢 정상';
const DEFAULT_IMPORTANCE = '중';

// common/exception/GlobalErrorCode.java 기준(모든 서비스 공용). 도메인별 에러 코드는 이
// 스크립트가 알 수 없으므로 팀원이 각 페이지의 이 표를 직접 보강해야 한다(그래서 매번 새로
// 그려도 되는 정보다).
const COMMON_ERROR_ROWS = [
  ['400', 'COMMON400', '잘못된 요청 파라미터/바디', '잘못된 요청입니다.'],
  ['401', 'COMMON401', '인증 토큰 없음/만료', '인증이 필요합니다.'],
  ['403', 'COMMON403', '접근 권한 없음', '접근 권한이 없습니다.'],
  ['404', 'COMMON404', '리소스를 찾을 수 없음', '요청한 리소스를 찾을 수 없습니다.'],
  ['405', 'COMMON405', '지원하지 않는 HTTP 메서드', '지원하지 않는 HTTP 메서드입니다.'],
  ['409', 'COMMON409', '요청이 현재 서버 상태와 충돌', '요청이 현재 서버 상태와 충돌합니다.'],
  ['500', 'COMMON500', '서버 내부 오류', '서버 내부 오류가 발생했습니다.'],
];

const STATUS_TEXT = { 200: 'OK', 201: 'Created', 202: 'Accepted', 204: 'No Content' };

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
  return { status: 'ERROR', message: '잘못된 요청입니다.', data: null, error: 'COMMON400', timestamp: '2026-09-14T00:00:00Z' };
}

function buildPageChildren(doc, ep) {
  const blocks = [];

  blocks.push(heading('📖 API 개요', 2));
  blocks.push(paragraph(ep.description || '(작성 필요)'));

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

// 중요도/검토 상태는 팀원이 노션에서 직접 판단해 바꾸는 값이라, 새로 만들 때만
// 기본값을 채우고 이미 있는 행은 절대 덮어쓰지 않는다 (피드백/수정요청과 같은 취급).
function buildProperties(ep, isNew) {
  const properties = {
    이름: { title: rt(ep.summary) },
    'PATH(endpoint)': { rich_text: rt(ep.path) },
    METHOD: { select: { name: ep.method } },
    Bearer: { checkbox: ep.bearer },
    도메인: { select: { name: SERVICE_DOMAIN } },
    상세: { select: { name: ep.tag } },
  };
  if (isNew) {
    properties['중요도'] = { select: { name: DEFAULT_IMPORTANCE } };
    properties['검토 상태'] = { select: { name: DEFAULT_REVIEW_STATUS } };
  }
  return properties;
}

function endpointFilter(ep) {
  return {
    and: [
      { property: 'PATH(endpoint)', rich_text: { equals: ep.path } },
      { property: 'METHOD', select: { equals: ep.method } },
    ],
  };
}

// ── 메인 ─────────────────────────────────────────────────────────────

async function main() {
  if (!SERVICE_DOMAIN) {
    fail('환경변수 SERVICE_DOMAIN이 설정되어 있지 않습니다 (예: SERVICE_DOMAIN=WMS). .env 또는 CI env에서 설정하세요.');
  }

  const specPath = resolveSpecPath();
  console.log(`OpenAPI 스펙 로드: ${path.relative(PROJECT_ROOT, specPath)}`);
  const doc = loadSpec(specPath);

  const endpoints = extractEndpoints(doc);
  console.log(`엔드포인트 ${endpoints.length}개 발견\n`);

  if (DRY_RUN) {
    const preview = endpoints.map((ep) => ({
      method: ep.method,
      path: ep.path,
      properties: buildProperties(ep, true),
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
  await validateDatabaseSchema(notion, dataSourceId, REQUIRED_PROPERTIES);

  const results = { created: 0, updated: 0, failed: [] };
  for (const ep of endpoints) {
    const label = `${ep.method} ${ep.path}`;
    process.stdout.write(`- ${label.padEnd(45)} ... `);
    try {
      const outcome = await upsertNotionPage(notion, dataSourceId, {
        filter: endpointFilter(ep),
        buildProperties: (isNew) => buildProperties(ep, isNew),
        buildChildren: () => buildPageChildren(doc, ep),
        label,
      });
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
