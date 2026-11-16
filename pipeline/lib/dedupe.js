import { tokenize } from './util.js';

const STOPWORDS = new Set(('a an and are as at be by for from has have in into is it its of on or that the this to was were will with '
  + 'after over new how why what who you your their his her they we our not can more than about up out says said').split(' '));

const RANK = { reject: 0, review: 1, publish: 2 };

export function titleTokens(title) {
  return new Set(tokenize(title).filter((w) => w.length > 2 && !STOPWORDS.has(w)));
}

/** Word-overlap similarity of two titles, 0 to 1. */
export function similarity(a, b) {
  if (!a.size || !b.size) return 0;
  let shared = 0;
  for (const w of a) if (b.has(w)) shared++;
  const jaccard = shared / (a.size + b.size - shared);
  // Short headline fully contained in a longer one counts as the same story.
  const overlap = shared / Math.min(a.size, b.size);
  return Math.min(a.size, b.size) >= 4 ? Math.max(jaccard, overlap * 0.85) : jaccard;
}

/**
 * Merge near-duplicate stories. The most trusted source becomes the main link,
 * the rest are listed in alsoCoveredBy.
 */
export function dedupe(stories, { threshold = 0.5 } = {}) {
  const sorted = [...stories].sort((a, b) => b.trust - a.trust || b.positivity - a.positivity);
  const clusters = [];

  for (const story of sorted) {
    const tokens = titleTokens(story.title);
    const match = clusters.find((c) => c.primary.id === story.id || similarity(c.tokens, tokens) >= threshold);
    if (match) match.others.push(story);
    else clusters.push({ primary: story, tokens, others: [] });
  }

  return clusters.map(({ primary, others }) => {
    const seen = new Set([primary.sourceName]);
    const alsoCoveredBy = [];
    for (const o of others) {
      if (seen.has(o.sourceName)) continue;
      seen.add(o.sourceName);
      alsoCoveredBy.push({ name: o.sourceName, link: o.link });
    }
    // If any outlet's version scored confidently positive, the merged story inherits that.
    const best = [primary, ...others].reduce((a, b) => (RANK[b.decision] > RANK[a.decision] ? b : a));
    return {
      ...primary,
      positivity: Math.max(primary.positivity, ...others.map((o) => o.positivity)),
      confidence: best.confidence,
      decision: best.decision,
      alsoCoveredBy,
    };
  });
}
