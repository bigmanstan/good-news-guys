#!/usr/bin/env node
// Apply decisions from data/review/*.json to the matching digests.
// "approve" adds the story to that day's digest, "reject" removes it if present.
// Decided items are then dropped from the review file; undecided ones stay.
//
//   node pipeline/apply-review.js            apply every review file
//   node pipeline/apply-review.js --dry-run
import { readdir, unlink } from 'node:fs/promises';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';
import { pickTop3 } from './lib/digest.js';
import { readJson, writeJson } from './lib/util.js';

const ROOT = fileURLToPath(new URL('../', import.meta.url));
const reviewDir = join(ROOT, 'data', 'review');
const digestDir = join(ROOT, 'data', 'digests');
const { values: args } = parseArgs({ options: { 'dry-run': { type: 'boolean', default: false } } });

const files = (await readdir(reviewDir)).filter((f) => /^\d{4}-\d{2}-\d{2}\.json$/.test(f));
let changed = 0;

for (const file of files) {
  const date = file.slice(0, 10);
  const review = await readJson(join(reviewDir, file));
  const decided = review.items.filter((i) => ['approve', 'reject'].includes(String(i.decision).trim().toLowerCase()));
  if (!decided.length) continue;

  const digestPath = join(digestDir, file);
  const digest = (await readJson(digestPath)) ?? { date, generatedAt: new Date().toISOString(), top3: [], stories: [] };
  const ids = new Set(digest.stories.map((s) => s.id));
  let added = 0;
  let removed = 0;

  for (const item of decided) {
    const decision = item.decision.trim().toLowerCase();
    if (decision === 'approve' && !ids.has(item.id)) {
      const { confidence, why, ...story } = item.story;
      digest.stories.push(story);
      ids.add(item.id);
      added++;
    } else if (decision === 'reject' && ids.has(item.id)) {
      digest.stories = digest.stories.filter((s) => s.id !== item.id);
      ids.delete(item.id);
      removed++;
    }
  }

  // Keep the current top 3 unless a story in it was rejected or there are open slots.
  digest.top3 = digest.top3.filter((id) => ids.has(id));
  if (digest.top3.length < 3) digest.top3 = pickTop3(digest.stories);

  const remaining = review.items.filter((i) => !decided.includes(i));
  console.log(`${date}: +${added} approved, -${removed} rejected, ${remaining.length} still undecided`);
  changed++;

  if (args['dry-run']) continue;
  await writeJson(digestPath, digest);
  if (remaining.length) await writeJson(join(reviewDir, file), { ...review, items: remaining });
  else await unlink(join(reviewDir, file));
}

if (!changed) console.log('No decisions to apply.');
