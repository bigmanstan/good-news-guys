import { stripHtml } from './util.js';

const MAX_BYTES = 400_000;
// content=(["'])…\1 so an apostrophe inside a double-quoted value doesn't cut it short.
const META = [
  /<meta[^>]+(?:property|name)=["']og:description["'][^>]*content=(["'])([\s\S]*?)\1/i,
  /<meta[^>]+content=(["'])([\s\S]*?)\1[^>]*(?:property|name)=["']og:description["']/i,
  /<meta[^>]+name=["']twitter:description["'][^>]*content=(["'])([\s\S]*?)\1/i,
  /<meta[^>]+name=["']description["'][^>]*content=(["'])([\s\S]*?)\1/i,
  /<meta[^>]+content=(["'])([\s\S]*?)\1[^>]*name=["']description["']/i,
];
const OG_IMAGE = /<meta[^>]+property=["']og:image["'][^>]*content=(["'])([\s\S]*?)\1/i;

/** Read the article's own <head> for a description and image. Only the first ~400 KB is read. */
export async function fetchPageMeta(url, { timeoutMs = 8000 } = {}) {
  const res = await fetch(url, {
    redirect: 'follow',
    signal: AbortSignal.timeout(timeoutMs),
    headers: { 'User-Agent': 'Mozilla/5.0 (compatible; GoodNewsGuysBot/0.1; daily RSS digest)', Accept: 'text/html' },
  });
  if (!res.ok || !/html/i.test(res.headers.get('content-type') ?? '')) {
    await res.body?.cancel();
    return null;
  }
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let html = '';
  while (html.length < MAX_BYTES) {
    const { done, value } = await reader.read();
    if (done) break;
    html += decoder.decode(value, { stream: true });
    if (/<\/head>/i.test(html)) break;
  }
  await reader.cancel().catch(() => {});
  return parseMeta(html);
}

export function parseMeta(html) {
  let description = '';
  for (const re of META) {
    const m = html.match(re);
    if (m) { description = stripHtml(m[2]); break; }
  }
  const image = html.match(OG_IMAGE)?.[2] ?? null;
  return { description, image };
}

/**
 * Fill in descriptions for items whose feed gave only a headline, by reading
 * the article page's meta description. Google News links are skipped: they
 * point to a Google redirect page, not the article.
 */
export async function enrichDescriptions(items, { limit = 60, concurrency = 6, minLength = 60 } = {}) {
  const todo = items
    .filter((i) => (i.description ?? '').length < minLength && !/news\.google\.com/.test(i.link))
    .slice(0, limit);
  let next = 0;
  let filled = 0;

  async function worker() {
    while (next < todo.length) {
      const item = todo[next++];
      try {
        const meta = await fetchPageMeta(item.link);
        if (meta?.description && meta.description.length >= minLength) {
          item.description = meta.description;
          filled++;
        }
        if (!item.image && meta?.image) item.image = meta.image;
      } catch {
        // Timeouts and blocked pages are expected; the item just stays headline-only.
      }
    }
  }

  await Promise.all(Array.from({ length: Math.min(concurrency, todo.length) }, worker));
  return { tried: todo.length, filled };
}
