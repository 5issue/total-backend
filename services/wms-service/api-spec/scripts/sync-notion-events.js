#!/usr/bin/env node
/**
 * TypeSpec으로 정의한 비동기 이벤트 명세(routes/events/*.events.tsp → main.tsp에 포함되어
 * REST와 같은 tsp-output/schema/*에 함께 컴파일됨)를 파싱해서 노션 "이벤트" 데이터베이스에
 * upsert(생성/갱신)한다.
 *
 * 이벤트는 routes/events/*.events.tsp에 HTTP 라우트 없이 순수 모델로만 정의한다(REST 쪽
 * routes/{internal,client}와 같은 자리지만 @route/@get/@post 대신 리터럴 타입 필드를 쓴다).
 * 하나의 이벤트는 producer/consumer/topic/exchange/queue/dataFormat/payloadType을 리터럴
 * 타입 필드로, 실제 메시지 바디는 payload 필드(models/events.dto.tsp 모델 참조)로 표현한다 —
 * 자세한 컨벤션은 routes/events/inventory.events.tsp 상단 주석 참고.
 *
 * REST용 스펙과 같은 openapi.yaml을 읽지만, 이 스크립트는 doc.paths가 아니라
 * doc.components.schemas 중 이벤트 메타데이터 필드를 전부 가진 것만 골라내므로
 * 일반 DTO와 섞여 있어도 문제없다 (extractEvents 참고).
 *
 * - 매칭 키: `Topic` 또는 `개요` 중 하나라도 같으면 같은 행으로 보고 갱신, 없으면 새로 만든다.
 * - `피드백/수정요청`, `검토 상태`는 절대 덮어쓰지 않는다 (업데이트 payload에 아예 포함하지 않음).
 *   `검토 상태`는 새 행을 만들 때만 기본값("🟢 정상")으로 채운다.
 * - `통신 방식`은 이 저장소에 RabbitMQ만 있어서 스크립트가 항상 "rabbitmq"로 채운다.
 *   Kafka 등을 쓰는 이벤트가 생기면 이벤트 모델에 `protocol` 리터럴 필드를 추가하고
 *   이 스크립트에서 읽어오도록 바꿔야 한다.
 *
 * 사용법:
 *   cd services/wms-service/api-spec
 *   npm run sync:notion:events
 *   # 또는: node scripts/sync-notion-events.js [path/to/openapi.yaml]
 *
 * 드라이런(노션 호출 없이 결과만 로컬 파일로 확인):
 *   node scripts/sync-notion-events.js --dry-run
 */

import 'dotenv/config';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Client } from '@notionhq/client';
import {
  fail,
  loadSpec,
  resolveSchema,
  generateExample,
  rt,
  heading,
  paragraph,
  callout,
  bulletedListItem,
  codeBlock,
  sleep,
  resolveDataSourceId,
  validateDatabaseSchema,
  upsertNotionPage,
} from './lib/notion-openapi.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = path.resolve(__dirname, '..');

const NOTION_TOKEN = process.env.NOTION_TOKEN;
const NOTION_EVENT_DATABASE_ID = process.env.NOTION_EVENT_DATABASE_ID;
const DRY_RUN = process.argv.includes('--dry-run') || process.env.DRY_RUN === 'true';

const DEFAULT_REVIEW_STATUS = '🟢 정상';
const DEFAULT_PROTOCOL = 'rabbitmq';

// 이 필드들이 모두 있는 스키마만 "이벤트 정의"로 취급한다 (payload 모델 자체는 제외됨).
const EVENT_METADATA_FIELDS = ['title', 'producer', 'consumer', 'topic', 'exchange', 'queue', 'dataFormat', 'payloadType', 'payload'];

const REQUIRED_PROPERTIES = {
  개요: 'title',
  Topic: 'rich_text',
  송신: 'multi_select',
  수신: 'multi_select',
  '통신 방식': 'select',
  '피드백/수정요청': 'rich_text',
  '검토 상태': 'select',
};

function resolveSpecPath() {
  const cliArg = process.argv.slice(2).find((arg) => !arg.startsWith('--'));
  if (cliArg) return path.resolve(process.cwd(), cliArg);
  if (process.env.EVENTS_SPEC_PATH) {
    return path.resolve(PROJECT_ROOT, process.env.EVENTS_SPEC_PATH);
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
      `EVENTS_SPEC_PATH 환경변수 / 첫 번째 인자로 경로를 지정하세요. (확인한 경로: ${candidates.join(', ')})`,
  );
}

// literal 타입 속성은 OpenAPI로 컴파일되면 { type: 'string', enum: ['값'] } 형태가 된다.
function literalValue(schema) {
  return schema?.enum?.[0];
}

function splitMulti(value) {
  return String(value ?? '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
}

// ── OpenAPI 문서 → 이벤트 목록 ─────────────────────────────────────────

function extractEvents(doc) {
  const events = [];
  for (const [name, schema] of Object.entries(doc.components?.schemas || {})) {
    const isEvent = EVENT_METADATA_FIELDS.every((field) => schema.properties?.[field]);
    if (!isEvent) continue;

    events.push({
      schemaName: name,
      title: literalValue(schema.properties.title),
      producer: splitMulti(literalValue(schema.properties.producer)),
      consumer: splitMulti(literalValue(schema.properties.consumer)),
      topic: literalValue(schema.properties.topic),
      exchange: literalValue(schema.properties.exchange),
      queue: literalValue(schema.properties.queue),
      dataFormat: literalValue(schema.properties.dataFormat),
      payloadType: literalValue(schema.properties.payloadType),
      overview: schema.description || '',
      payloadSchema: schema.properties.payload,
    });
  }
  return events;
}

// ── 페이지 본문 조립 ─────────────────────────────────────────────────────

function buildHeaderExample(ev) {
  return { content_type: 'application/json', __TypeId__: ev.payloadType };
}

function buildEventPageChildren(doc, ev) {
  const blocks = [];

  blocks.push(
    callout(
      [`Producer = ${ev.producer.join(', ')}`, `Topic = ${ev.topic}`, `Consumer = ${ev.consumer.join(', ')}`, `Payload = ${ev.payloadType}`].join(
        '\n',
      ),
      '📌',
    ),
  );

  blocks.push(heading('📖 통신 개요', 2));
  blocks.push(paragraph(ev.overview || '(작성 필요)'));

  blocks.push(heading('⚙️ 이벤트 라우팅 상세', 2));
  blocks.push(bulletedListItem(`Producer: ${ev.producer.join(', ')}`));
  blocks.push(bulletedListItem(`Consumer: ${ev.consumer.join(', ')}`));
  blocks.push(bulletedListItem(`Topic: ${ev.topic}`));
  blocks.push(bulletedListItem(`데이터 포맷: ${ev.dataFormat}`));
  blocks.push(bulletedListItem(`Exchange: ${ev.exchange}`));
  blocks.push(bulletedListItem(`Queue: ${ev.queue}`));

  blocks.push(heading('🔹 Header', 2));
  blocks.push(codeBlock(buildHeaderExample(ev)));

  blocks.push(heading('🔹 Body Payload', 2));
  blocks.push(codeBlock(generateExample(doc, ev.payloadSchema)));

  return blocks;
}

function buildEventProperties(ev, isNew) {
  const properties = {
    개요: { title: rt(ev.title) },
    Topic: { rich_text: rt(ev.topic) },
    송신: { multi_select: ev.producer.map((name) => ({ name })) },
    수신: { multi_select: ev.consumer.map((name) => ({ name })) },
    '통신 방식': { select: { name: DEFAULT_PROTOCOL } },
  };
  if (isNew) {
    properties['검토 상태'] = { select: { name: DEFAULT_REVIEW_STATUS } };
  }
  return properties;
}

function eventFilter(ev) {
  return {
    or: [{ property: 'Topic', rich_text: { equals: ev.topic } }, { property: '개요', title: { equals: ev.title } }],
  };
}

// ── 메인 ─────────────────────────────────────────────────────────────

async function main() {
  const specPath = resolveSpecPath();
  console.log(`이벤트 OpenAPI 스펙 로드: ${path.relative(PROJECT_ROOT, specPath)}`);
  const doc = loadSpec(specPath);

  const events = extractEvents(doc);
  console.log(`이벤트 ${events.length}개 발견\n`);

  if (DRY_RUN) {
    const preview = events.map((ev) => ({
      title: ev.title,
      topic: ev.topic,
      properties: buildEventProperties(ev, true),
      children: buildEventPageChildren(doc, ev),
    }));
    const outPath = path.join(PROJECT_ROOT, 'tsp-output', 'notion-events-dry-run.json');
    fs.mkdirSync(path.dirname(outPath), { recursive: true });
    fs.writeFileSync(outPath, JSON.stringify(preview, null, 2));
    for (const ev of events) {
      console.log(`- ${ev.topic}  (${ev.title})`);
    }
    console.log(`\n[dry-run] 노션 API를 호출하지 않았습니다. 생성될 내용은 여기서 확인하세요:`);
    console.log(`  ${path.relative(PROJECT_ROOT, outPath)}`);
    return;
  }

  if (!NOTION_TOKEN) fail('환경변수 NOTION_TOKEN이 설정되어 있지 않습니다 (.env 또는 GitHub Actions secret 확인).');
  if (!NOTION_EVENT_DATABASE_ID) fail('환경변수 NOTION_EVENT_DATABASE_ID가 설정되어 있지 않습니다 (.env 또는 GitHub Actions secret 확인).');

  const notion = new Client({ auth: NOTION_TOKEN });
  const dataSourceId = await resolveDataSourceId(notion, NOTION_EVENT_DATABASE_ID);
  await validateDatabaseSchema(notion, dataSourceId, REQUIRED_PROPERTIES);

  const results = { created: 0, updated: 0, failed: [] };
  for (const ev of events) {
    const label = `${ev.topic}`;
    process.stdout.write(`- ${label.padEnd(45)} ... `);
    try {
      const outcome = await upsertNotionPage(notion, dataSourceId, {
        filter: eventFilter(ev),
        buildProperties: (isNew) => buildEventProperties(ev, isNew),
        buildChildren: () => buildEventPageChildren(doc, ev),
        label,
      });
      results[outcome] += 1;
      console.log(outcome === 'created' ? '생성됨' : '갱신됨');
    } catch (err) {
      results.failed.push({ ev, err });
      console.log(`실패 (${err.message})`);
    }
    await sleep(200);
  }

  console.log('\n--- 동기화 결과 ---');
  console.log(`생성: ${results.created}, 갱신: ${results.updated}, 실패: ${results.failed.length}`);
  if (results.failed.length) {
    console.log('\n실패한 이벤트:');
    for (const { ev, err } of results.failed) {
      console.log(`  - ${ev.topic}: ${err.message}`);
    }
    process.exitCode = 1;
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
