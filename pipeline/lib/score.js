import Sentiment from 'sentiment';
import { tokenize } from './util.js';

function escapeRegex(s) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/** Build the filter + scorer from config/scoring.json. */
export function createScorer(config) {
  const sentiment = new Sentiment();
  const extras = { ...config.boost, ...config.penalty };
  const t = config.thresholds;
  const blockRe = config.blocklist.length
    ? new RegExp(`\\b(${config.blocklist.map(escapeRegex).join('|')})\\b`, 'i')
    : null;

  const skipRes = (config.skipPatterns ?? []).map((p) => new RegExp(p, 'i'));
  const blockedOutlets = new Set((config.blockedOutlets ?? []).map((o) => o.toLowerCase()));

  const sportsRe = config.sportsTerms?.length
    ? new RegExp(`(?<![\\w'])(${config.sportsTerms.map(escapeRegex).join('|')})(?![\\w'])`, 'gi')
    : null;

  /** How many different sports terms appear in the headline + description. */
  function sportsHits(story) {
    if (!sportsRe) return 0;
    const found = `${story.title} ${story.description}`.match(sportsRe) ?? [];
    return new Set(found.map((f) => f.toLowerCase())).size;
  }

  /** Why the story is rejected before scoring ("word: killed", "topic", "sports", "outlet"), or null. */
  function blocked(story) {
    if (blockedOutlets.has(story.sourceName.toLowerCase())) return 'outlet';
    if (skipRes.some((re) => re.test(story.title))) return 'topic';
    if (sportsHits(story) >= (config.sportsThreshold ?? 2)) return 'sports';
    const m = blockRe && `${story.title} ${story.description}`.match(blockRe);
    return m ? `word: ${m[1].toLowerCase()}` : null;
  }

  // AFINN's comparative is score / token count, which lets a single "win" in a
  // five-word headline look ecstatic. A floor on the denominator calms short text.
  function analyze(text) {
    if (!text) return null;
    const r = sentiment.analyze(text, { extras });
    return { ...r, comparative: r.score / Math.max(r.tokens.length, 10) };
  }

  /**
   * positivity: 0 (very negative) to 1 (very positive), 0.5 is neutral.
   * confidence: 0 to 1, how much signal the scorer actually had.
   */
  function score(story) {
    const title = analyze(story.title);
    const desc = analyze(story.description);

    // The headline carries the most weight; the description adds context when present.
    const comparative = desc ? 0.6 * title.comparative + 0.4 * desc.comparative : title.comparative;
    let positivity = 1 / (1 + Math.exp(-6 * comparative));
    if (story.curated) positivity = Math.min(1, positivity + t.curatedBonus);

    const hits = new Set([...title.positive, ...title.negative, ...(desc?.positive ?? []), ...(desc?.negative ?? [])]).size;
    const mixed = (title.positive.length + (desc?.positive.length ?? 0)) > 0
      && (title.negative.length + (desc?.negative.length ?? 0)) > 0;
    let confidence = Math.min(1, Math.abs(positivity - 0.5) * 2.5) * Math.min(1, hits / 3);
    if (mixed) confidence *= 0.7;
    if (!desc) confidence *= t.headlineOnlyConfidence ?? 1;

    const label = positivity >= 0.6 ? 'positive' : positivity <= 0.4 ? 'negative' : 'neutral';
    const noNegatives = !title.negative.length && !(desc?.negative.length);

    let decision = 'reject';
    if (positivity >= t.publishPositivity && confidence >= t.publishConfidence) decision = 'publish';
    else if (story.neutralOk && positivity >= 0.5 && noNegatives) decision = 'publish';
    else if (positivity >= t.reviewPositivity) decision = 'review';
    // A headline with no description needs two positive words, not one stray "win".
    if (decision === 'publish' && !desc && !story.neutralOk && new Set(title.positive).size < 2) decision = 'review';
    // Google News gives headlines only and links to a redirect, so a person always checks these.
    if (decision === 'publish' && story.aggregator) decision = 'review';

    return {
      positivity: round(positivity),
      confidence: round(confidence),
      label,
      decision,
      signals: {
        positive: [...new Set([...title.positive, ...(desc?.positive ?? [])])],
        negative: [...new Set([...title.negative, ...(desc?.negative ?? [])])],
      },
    };
  }

  /** Pick a category from keyword hits, falling back to the feed's own category. */
  function categorize(story) {
    const text = ` ${tokenize(`${story.title} ${story.description}`).join(' ')} `;
    let best = null;
    let bestHits = 0;
    for (const [cat, keywords] of Object.entries(config.categories)) {
      const hits = keywords.filter((k) => text.includes(` ${k} `)).length;
      if (hits > bestHits) { best = cat; bestHits = hits; }
    }
    if (story.category !== 'general' && story.category !== 'local' && bestHits < 2) return story.category;
    return best ?? (story.category === 'local' ? 'community' : story.category);
  }

  return { blocked, score, categorize };
}

function round(n) {
  return Math.round(n * 100) / 100;
}
