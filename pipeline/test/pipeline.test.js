import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { stripHtml, canonicalUrl, dateInToronto } from '../lib/util.js';
import { normalizeItem } from '../lib/normalize.js';
import { createScorer } from '../lib/score.js';
import { dedupe, similarity, titleTokens } from '../lib/dedupe.js';
import { summarize, readTime } from '../lib/summarize.js';
import { mergeDigest, mergeReview, pickTop3 } from '../lib/digest.js';
import { parseMeta } from '../lib/enrich.js';

const scoring = JSON.parse(readFileSync(new URL('../../config/scoring.json', import.meta.url)));
const scorer = createScorer(scoring);

const base = { sourceName: 'X', trust: 3, curated: false, category: 'general', region: null, description: '', fullText: '' };

test('stripHtml handles tags and double-encoded entities', () => {
  assert.equal(stripHtml('<p>Fish &amp;amp; chips&nbsp;<b>today</b></p>'), 'Fish & chips today');
  assert.equal(stripHtml('&lt;p&gt;Hello&lt;/p&gt;'), 'Hello');
});

test('canonicalUrl drops tracking params', () => {
  assert.equal(canonicalUrl('https://a.com/x?utm_source=rss&id=4#top'), 'https://a.com/x?id=4');
});

test('dateInToronto uses Toronto time, not UTC', () => {
  // 03:00 UTC on the 17th is still the evening of the 16th in Toronto.
  assert.equal(dateInToronto(new Date('2026-11-17T03:00:00Z')), '2026-11-16');
});

test('normalize splits Google News "Headline - Outlet" titles', () => {
  const s = normalizeItem(
    { title: 'Toronto library opens new branch - CityNews Toronto', link: 'https://news.google.com/x', description: 'junk' },
    { id: 'local-toronto', name: 'Google News', aggregator: true, region: 'toronto', category: 'local', trust: 2 },
  );
  assert.equal(s.title, 'Toronto library opens new branch');
  assert.equal(s.sourceName, 'CityNews Toronto');
  assert.equal(s.description, '');
});

test('normalize rejects items without a usable link', () => {
  assert.equal(normalizeItem({ title: 'Hi' }, { id: 'x', name: 'X' }), null);
});

test('skip patterns catch sports results and stream spam', () => {
  assert.equal(scorer.blocked({ ...base, title: 'Senators top Penguins 4-1' }), 'topic');
  assert.equal(scorer.blocked({ ...base, title: 'Canucks vs Golden Knights FREE LIVE stream' }), 'topic');
  assert.equal(scorer.blocked({ ...base, title: 'Toronto library opens new branch' }), null);
});

test('sports detector needs two different sports terms', () => {
  assert.equal(scorer.blocked({ ...base, title: 'Levi, McDavid help Oilers top Maple Leafs' }), 'sports');
  assert.equal(scorer.blocked({ ...base, title: 'Senators grind out a win', description: 'Ottawa scored twice in the third period.' }), 'sports');
  // One stray match is fine: a "game" in a non-sports story shouldn't drop it.
  assert.equal(scorer.blocked({ ...base, title: 'Library board game night draws record crowd' }), null);
});

test('a single positive word in a short headline is not enough to publish', () => {
  assert.notEqual(scorer.score({ ...base, title: 'Toronto Tempo win lottery' }).decision, 'publish');
});

test('neutralOk feeds publish neutral discoveries with no negative words', () => {
  assert.equal(scorer.score({ ...base, neutralOk: true, title: 'Dinosaurs may have eaten seaweed 100 million years ago' }).decision, 'publish');
  assert.equal(scorer.score({ ...base, neutralOk: true, title: 'Glacier ice loss worsens in warming crisis' }).decision, 'reject');
});

test('blocklist matches whole words only', () => {
  assert.equal(scorer.blocked({ ...base, title: 'Two killed in crash' }), 'word: killed');
  assert.equal(scorer.blocked({ ...base, title: 'Local teacher wins national award' }), null); // "award" contains "war"
});

test('clearly positive headline publishes, negative one is rejected', () => {
  const good = scorer.score({ ...base, title: 'Volunteers rescued 200 sea turtles and the hatchlings are thriving', description: 'A heartwarming success for the community.' });
  assert.equal(good.decision, 'publish');
  const bad = scorer.score({ ...base, title: 'Shortage crisis threatens hospitals as fears grow', description: 'Officials warn of decline.' });
  assert.equal(bad.decision, 'reject');
});

test('neutral headline from a curated source goes to review, not publish', () => {
  const r = scorer.score({ ...base, curated: true, title: 'City council meets on Tuesday to discuss the budget' });
  assert.equal(r.decision, 'review');
});

test('categorize picks keyword category over a general feed', () => {
  assert.equal(scorer.categorize({ ...base, title: 'NASA telescope spots a new planet orbiting a nearby star' }), 'space');
});

test('similar titles merge and the most trusted source wins', () => {
  const a = { ...base, id: 'a', title: 'Scientists discover new species of glowing shark off New Zealand', trust: 3, sourceName: 'Blog', link: 'https://blog/x', positivity: 0.7, confidence: 0.5, decision: 'publish' };
  const b = { ...base, id: 'b', title: 'New species of glowing shark discovered off New Zealand coast', trust: 5, sourceName: 'BBC News', link: 'https://bbc/x', positivity: 0.6, confidence: 0.2, decision: 'review' };
  const c = { ...base, id: 'c', title: 'City opens free skating rink downtown', trust: 4, sourceName: 'CBC', link: 'https://cbc/x', positivity: 0.7, confidence: 0.5, decision: 'publish' };
  const out = dedupe([a, b, c]);
  assert.equal(out.length, 2);
  const shark = out.find((s) => s.title.includes('shark'));
  assert.equal(shark.sourceName, 'BBC News');
  assert.deepEqual(shark.alsoCoveredBy, [{ name: 'Blog', link: 'https://blog/x' }]);
  assert.equal(shark.decision, 'publish'); // inherits the confident version's decision
});

test('unrelated titles are not similar', () => {
  assert.ok(similarity(titleTokens('Dog adopted after 500 days'), titleTokens('Solar farm powers 10,000 homes')) < 0.2);
});

test('summarize keeps 2–3 sentences and drops WordPress boilerplate', () => {
  const s = summarize({ title: 'T', description: 'One thing happened. Then another. And a third! A fourth too. The post T appeared first on Blog.' });
  assert.equal(s, 'One thing happened. Then another. And a third!');
});

test('summarize strips all-caps bylines and scraped menu text', () => {
  assert.equal(
    summarize({ title: 'T', description: 'BY THE OPTIMIST DAILY EDITORIAL TEAM You know that feeling. It passes.' }),
    'You know that feeling. It passes.',
  );
  assert.equal(
    summarize({ title: 'T', description: 'APOD Archive Submissions Index Search Calendar RSS Education About APOD. A nebula glows with new stars.' }),
    'A nebula glows with new stars.',
  );
});

test('summarize drops a cut-off trailing fragment', () => {
  assert.equal(summarize({ title: 'T', description: 'A clinic did a first. Doctors used donor marrow on a patient who received' }), 'A clinic did a first.');
  assert.equal(summarize({ title: 'T', description: 'Doctors used donor marrow on a patient who received' }), 'Doctors used donor marrow on a patient who received…');
});

test('parseMeta reads og:description, keeping apostrophes', () => {
  const html = `<head><meta property="og:description" content="The city's new park opens with free skating &amp; music."><meta property="og:image" content="https://x/img.jpg"></head>`;
  assert.deepEqual(parseMeta(html), { description: "The city's new park opens with free skating & music.", image: 'https://x/img.jpg' });
  assert.equal(parseMeta('<meta content="Plain description here" name="description">').description, 'Plain description here');
});

test('Google News stories never auto-publish', () => {
  const r = scorer.score({ ...base, aggregator: true, title: 'Volunteers rescued puppies and the community celebrates a heartwarming reunion' });
  assert.equal(r.decision, 'review');
});

test('readTime falls back when there is no full text', () => {
  assert.equal(readTime({ fullText: '' }), 3);
  assert.equal(readTime({ fullText: 'word '.repeat(690) }), 3);
  assert.equal(readTime({ fullText: 'word '.repeat(1150) }), 5);
});

test('re-running a day keeps pinned flags and earlier stories', () => {
  const now = Date.parse('2026-11-16T12:00:00Z');
  const old = { stories: [{ id: 'p', headline: 'Pinned', pinned: true, positivity: 0.6, category: 'science', region: null, summary: 'kept' }] };
  const fresh = [
    { id: 'p', headline: 'Pinned', pinned: false, positivity: 0.6, category: 'science', region: null, summary: '' },
    { id: 'n', headline: 'New', pinned: false, positivity: 0.9, category: 'space', region: null, summary: 'x' },
  ];
  const d = mergeDigest(old, fresh, '2026-11-16', now);
  assert.equal(d.stories.length, 2);
  assert.equal(d.stories.find((s) => s.id === 'p').pinned, true);
  assert.equal(d.stories.find((s) => s.id === 'p').summary, 'kept');
  assert.equal(d.top3[0], 'p');
});

test('top 3 skips local stories', () => {
  const ids = pickTop3([
    { id: 'l', region: 'toronto', positivity: 1, category: 'community' },
    { id: 'g', region: null, positivity: 0.6, category: 'science' },
  ]);
  assert.deepEqual(ids, ['g']);
});

test('review merge keeps decisions you already made', () => {
  const r = mergeReview({ items: [{ id: 'a', decision: 'approve' }] }, [{ id: 'a', headline: 'A', positivity: 0.5 }, { id: 'b', headline: 'B', positivity: 0.5 }], 'd');
  assert.equal(r.items.find((i) => i.id === 'a').decision, 'approve');
  assert.equal(r.items.find((i) => i.id === 'b').decision, '');
});
