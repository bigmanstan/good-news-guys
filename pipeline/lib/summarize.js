const BOILERPLATE = [
  /The post .+? appeared first on .+?\.?$/i,
  /Continue reading\.*$/i,
  /Read more\.*$/i,
  /\[(…|\.\.\.)\]\s*$/,
  /…\s*$/,
];

function clean(text) {
  let t = text;
  for (const re of BOILERPLATE) t = t.replace(re, '').trim();
  // All-caps byline at the start: "BY THE OPTIMIST DAILY EDITORIAL TEAM You know…"
  t = t.replace(/^BY [A-Z0-9 .,'&-]+?\s+(?=[A-Z][a-z])/, '');
  return t;
}

/** Menu/link text scraped into a description: mostly Capitalized Words, little else. */
function looksLikeNav(sentence) {
  const words = sentence.split(/\s+/).filter((w) => /[a-z]/i.test(w));
  if (words.length < 5) return false;
  const caps = words.filter((w) => /^[A-Z]/.test(w)).length;
  return caps / words.length > 0.7;
}

export function splitSentences(text) {
  return text.split(/(?<=[.!?])\s+(?=[A-Z0-9"“‘'(])/).map((s) => s.trim()).filter(Boolean);
}

/**
 * Extractive summary: the first 2–3 sentences of the feed's description,
 * capped near 320 characters. Plain code, no model involved.
 */
export function summarize(story, { maxChars = 320 } = {}) {
  const source = clean(story.description || story.fullText || '');
  if (!source) return '';
  const sentences = splitSentences(source).filter((s) => !looksLikeNav(s));
  if (!sentences.length) return '';
  const out = [];
  for (const s of sentences) {
    if (out.length >= 3) break;
    if (out.length >= 2 && out.join(' ').length + s.length > maxChars) break;
    out.push(s);
  }
  // Feeds often cut descriptions off mid-sentence. Drop the fragment if we have
  // a complete sentence before it, otherwise mark it as truncated.
  const last = out[out.length - 1];
  if (!/[.!?]["”’')]?$/.test(last)) {
    if (out.length > 1) out.pop();
    else out[0] = `${last.replace(/[\s,;:–—-]+$/, '')}…`;
  }
  let summary = out.join(' ');
  if (summary.length > maxChars + 80) {
    summary = summary.slice(0, maxChars).replace(/\s+\S*$/, '') + '…';
  }
  // Skip summaries that only repeat the headline.
  if (summary.toLowerCase().startsWith(story.title.toLowerCase()) && summary.length < story.title.length + 20) return '';
  return summary;
}

/** Minutes to read, from full text when the feed has it, else a typical default. */
export function readTime(story, { wpm = 230, fallback = 3 } = {}) {
  const words = (story.fullText || '').split(/\s+/).filter(Boolean).length;
  return words > 150 ? Math.max(1, Math.round(words / wpm)) : fallback;
}
