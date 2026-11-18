#!/usr/bin/env node
// Daily pipeline: fetch → normalize → filter → score → dedupe → summarize → write.
//
//   node pipeline/run.js                 build today's digest (Toronto date)
//   node pipeline/run.js --dry-run       print results, write nothing
//   node pipeline/run.js --date 2026-11-16
//   node pipeline/run.js --scheduled     skip unless it's 6 AM+ in Toronto and today's digest is missing
//   node pipeline/run.js --no-local      skip local (city) feeds
//   node pipeline/run.js --only gnn,nasa only these source ids
import { parseArgs } from 'node:util';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { fetchAll, cityFeed } from './lib/fetch.js';
import { normalizeItem } from './lib/normalize.js';
import { enrichDescriptions } from './lib/enrich.js';
import { createScorer } from './lib/score.js';
import { dedupe } from './lib/dedupe.js';
import { createPlaceFinder } from './lib/places.js';
import { toStory, mergeDigest, mergeReview } from './lib/digest.js';
import { dateInToronto, hourInToronto, readJson, writeJson } from './lib/util.js';

const ROOT = fileURLToPath(new URL('../', import.meta.url));
const path = (...p) => join(ROOT, ...p);

const { values: args } = parseArgs({
  options: {
    'dry-run': { type: 'boolean', default: false },
    scheduled: { type: 'boolean', default: false },
    'no-local': { type: 'boolean', default: false },
    date: { type: 'string' },
    only: { type: 'string' },
  },
});

const date = args.date ?? dateInToronto();
if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) throw new Error(`--date must be YYYY-MM-DD, got "${date}"`);
const digestPath = path('data', 'digests', `${date}.json`);
const reviewPath = path('data', 'review', `${date}.json`);

if (args.scheduled) {
  const existing = await readJson(digestPath);
  if (hourInToronto() < 6) {
    console.log(`Skipping: it's before 6 AM in Toronto.`);
    process.exit(0);
  }
  if (existing) {
    console.log(`Skipping: ${date} digest already exists.`);
    process.exit(0);
  }
}

/** Headline reduced to its first words, so small edits between cities still match. */
function wireKey(title) {
  return title.toLowerCase().replace(/[^a-z0-9 ]/g, '').split(/\s+/).filter(Boolean).slice(0, 8).join(' ');
}

function daysBefore(d, n) {
  return new Date(Date.parse(`${d}T12:00:00Z`) - n * 864e5).toISOString().slice(0, 10);
}

const [sourcesCfg, citiesCfg, scoringCfg] = await Promise.all([
  readJson(path('config', 'sources.json')),
  readJson(path('config', 'cities.json')),
  readJson(path('config', 'scoring.json')),
]);

let sources = sourcesCfg.feeds.filter((f) => f.enabled !== false);
if (args['no-local']) sources = sources.filter((s) => !s.region);
else sources.push(...citiesCfg.cities.filter((c) => c.googleNews).map(cityFeed));
if (args.only) {
  const ids = new Set(args.only.split(','));
  sources = sources.filter((s) => ids.has(s.id));
}

// 1. Fetch
const started = Date.now();
const { results, errors } = await fetchAll(sources);
console.log(`Fetched ${results.length}/${sources.length} feeds in ${((Date.now() - started) / 1000).toFixed(1)}s`);
for (const e of errors) console.warn(`  ! ${e.source.id}: ${e.error}`);
if (!results.length) {
  console.error('Every feed failed; not writing anything.');
  process.exit(1);
}

// 2. Normalize, and skip anything already in the last 5 digests
const seen = new Set();
for (let n = 1; n <= 5; n++) {
  const past = await readJson(path('data', 'digests', `${daysBefore(date, n)}.json`));
  for (const s of past?.stories ?? []) seen.add(s.id);
}
const maxAgeMs = scoringCfg.maxAgeHours * 36e5;
// Local feeds publish less often, so their stories may be a little older.
const localMaxAgeMs = (scoringCfg.localMaxAgeHours ?? scoringCfg.maxAgeHours) * 36e5;
const now = Date.now();
const stats = { wire: 0, raw: 0, stale: 0, repeat: 0, blocked: 0, publish: 0, review: 0, reject: 0 };
const blockedTerms = {};

const scorer = createScorer(scoringCfg, citiesCfg.cities);
const findPlaces = createPlaceFinder(citiesCfg.cities);
const byId = new Set();
const fresh = [];

function isBlocked(item) {
  const hit = scorer.blocked(item);
  if (hit) { stats.blocked++; blockedTerms[hit] = (blockedTerms[hit] ?? 0) + 1; }
  return Boolean(hit);
}

for (const { source, items } of results) {
  for (const raw of items) {
    stats.raw++;
    const item = normalizeItem(raw, source);
    if (!item || byId.has(item.id)) continue;
    byId.add(item.id);
    if (item.publishedAt && now - Date.parse(item.publishedAt) > (item.region ? localMaxAgeMs : maxAgeMs)) { stats.stale++; continue; }
    if (seen.has(item.id)) { stats.repeat++; continue; }
    // 3a. Blocklist on the headline before spending a page fetch on it
    if (!isBlocked(item)) fresh.push(item);
  }
}

// The same headline in two cities' feeds is national wire copy, not local news.
const regionsByTitle = new Map();
for (const item of fresh) {
  if (!item.region) continue;
  const key = wireKey(item.title);
  regionsByTitle.set(key, (regionsByTitle.get(key) ?? new Set()).add(item.region));
}
for (const item of fresh) {
  if (item.region && regionsByTitle.get(wireKey(item.title)).size > 1) {
    item.wire = true;
    item.region = null;
    stats.wire++;
  }
}

// 3b. Headline-only items: read the article page's meta description
const enriched = await enrichDescriptions(fresh);
if (enriched.tried) console.log(`Descriptions: filled ${enriched.filled}/${enriched.tried} headline-only items from article pages`);

// 3c. Blocklist again (descriptions can reveal what the headline hid), then score
const candidates = [];
for (const item of fresh) {
  if (item.description && isBlocked(item)) continue;
  candidates.push({ ...item, ...scorer.score(item), category: scorer.categorize(item), places: findPlaces(item) });
}

// 4. Dedupe across outlets
const merged = dedupe(candidates.filter((c) => c.decision !== 'reject'));
for (const c of candidates) if (c.decision === 'reject') stats.reject++;

// 5. Summarize and shape
const publish = [];
const review = [];
for (const s of merged) {
  const story = toStory(s, date);
  if (s.decision === 'publish') publish.push(story);
  else review.push({ ...story, confidence: s.confidence, why: describe(s) });
}
// Keep the queue short enough for a person to get through over coffee.
review.sort((a, b) => b.positivity * (0.5 + b.confidence) - a.positivity * (0.5 + a.confidence));
const reviewDropped = Math.max(0, review.length - scoringCfg.reviewLimit);
review.length = Math.min(review.length, scoringCfg.reviewLimit);

function describe(s) {
  const parts = [`positivity ${s.positivity}`, `confidence ${s.confidence}`];
  if (s.signals.positive.length) parts.push(`+ ${s.signals.positive.join(', ')}`);
  if (s.signals.negative.length) parts.push(`- ${s.signals.negative.join(', ')}`);
  return parts.join(' | ');
}

// 6. Write
const existingDigest = await readJson(digestPath);
const digest = mergeDigest(existingDigest, publish, date);
const inDigest = new Set(digest.stories.map((s) => s.id));
const reviewFile = mergeReview(await readJson(reviewPath), review.filter((s) => !inDigest.has(s.id)), date);
stats.publish = publish.length;
stats.review = reviewFile.items.filter((i) => !i.decision).length;

console.log(`Items: ${stats.raw} raw, ${stats.stale} too old, ${stats.repeat} already published, ${stats.blocked} blocklisted, ${stats.reject} scored negative, ${stats.wire} wire copies moved out of local`);
console.log(`Result: ${digest.stories.length} stories in digest (${stats.publish} new), ${stats.review} waiting for review${reviewDropped ? ` (${reviewDropped} weaker ones dropped)` : ''}`);
const topTerms = Object.entries(blockedTerms).sort((a, b) => b[1] - a[1]).slice(0, 10);
if (topTerms.length) console.log(`Filtered out by: ${topTerms.map(([t, n]) => `${t} (${n})`).join(', ')}`);

const top = digest.top3.map((id) => digest.stories.find((s) => s.id === id));
console.log('\nTop 3:');
for (const s of top) console.log(`  ★ [${s.category}] ${s.headline} — ${s.sourceName}`);

if (args['dry-run']) {
  console.log('\nAll published stories:');
  for (const s of digest.stories) console.log(`  ${s.positivity.toFixed(2)} [${s.category}${s.region ? `/${s.region}` : ''}] ${s.headline} — ${s.sourceName}`);
  console.log('\nReview queue:');
  for (const i of reviewFile.items) console.log(`  ${i.positivity.toFixed(2)} ${i.headline} — ${i.sourceName}  (${i.why})`);
  console.log('\nDry run: nothing written.');
} else {
  await writeJson(digestPath, digest);
  console.log(`\nWrote ${digestPath}`);
  if (reviewFile.items.length) {
    await writeJson(reviewPath, reviewFile);
    console.log(`Wrote ${reviewPath}`);
  }
}

// A feed that timed out can leave a socket open and keep Node alive; we're done.
process.exit(0);
