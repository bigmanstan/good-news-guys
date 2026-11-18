import { summarize, readTime } from './summarize.js';

/** The Story shape the Astro site reads. Keep this small. */
export function toStory(s, date) {
  return {
    id: s.id,
    headline: s.title,
    summary: summarize(s),
    sourceName: s.sourceName,
    sourceLink: s.link,
    alsoCoveredBy: s.alsoCoveredBy ?? [],
    category: s.category,
    region: s.region,
    // Neighbourhoods or nearby towns the story mentions, with coordinates for the app.
    places: s.places ?? [],
    positivity: s.positivity,
    readTime: readTime(s),
    publishedAt: s.publishedAt,
    digestDate: date,
    image: s.image,
    pinned: false,
  };
}

function rank(story, now) {
  const ageHours = story.publishedAt ? (now - Date.parse(story.publishedAt)) / 36e5 : 24;
  const freshness = Math.max(0, 1 - ageHours / 48);
  const coverage = Math.min(1, (story.alsoCoveredBy?.length ?? 0) / 3);
  const summaryBonus = story.summary ? 0.1 : 0;
  return story.positivity * 0.5 + freshness * 0.2 + coverage * 0.2 + summaryBonus + (story.pinned ? 10 : 0);
}

/** Top 3: pinned first, then best-ranked, non-local, one per category where possible. */
export function pickTop3(stories, now = Date.now()) {
  const pool = stories.filter((s) => !s.region).sort((a, b) => rank(b, now) - rank(a, now));
  const top = [];
  const cats = new Set();
  for (const s of pool) {
    if (top.length === 3) break;
    if (s.pinned || !cats.has(s.category)) { top.push(s); cats.add(s.category); }
  }
  for (const s of pool) {
    if (top.length === 3) break;
    if (!top.includes(s)) top.push(s);
  }
  return top.map((s) => s.id);
}

/**
 * Combine a fresh run with whatever is already saved for that date, so a re-run
 * never drops pinned or hand-approved stories, or flips a pinned flag back off.
 */
export function mergeDigest(existing, fresh, date, now = Date.now()) {
  const byId = new Map();
  for (const s of fresh) byId.set(s.id, s);
  for (const s of existing?.stories ?? []) {
    if (byId.has(s.id)) byId.set(s.id, { ...byId.get(s.id), pinned: s.pinned, summary: s.summary || byId.get(s.id).summary });
    else byId.set(s.id, s);
  }
  const stories = [...byId.values()].sort((a, b) => rank(b, now) - rank(a, now));
  return {
    date,
    generatedAt: new Date(now).toISOString(),
    top3: pickTop3(stories, now),
    stories,
  };
}

/** Review file: one entry per uncertain story. Set decision to "approve" or "reject". */
export function mergeReview(existing, candidates, date) {
  const prior = new Map((existing?.items ?? []).map((i) => [i.id, i]));
  const items = candidates.map((s) => {
    const old = prior.get(s.id);
    prior.delete(s.id);
    return {
      id: s.id,
      decision: old?.decision ?? '',
      headline: s.headline,
      sourceName: s.sourceName,
      link: s.sourceLink,
      positivity: s.positivity,
      confidence: s.confidence,
      why: s.why,
      story: s,
    };
  });
  // Keep items you already decided on even if today's re-run no longer surfaces them.
  for (const old of prior.values()) if (old.decision) items.push(old);
  return {
    date,
    howTo: 'Set "decision" to "approve" or "reject" for each item, then merge this PR. Approved stories are added to the digest for this date. Blank means skip.',
    items,
  };
}
