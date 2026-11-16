import { stripHtml, canonicalUrl, shortHash } from './util.js';

function firstUrl(list) {
  if (!Array.isArray(list)) return null;
  for (const m of list) {
    const url = m?.$?.url;
    if (url && (!m.$.medium || m.$.medium === 'image')) return url;
  }
  return null;
}

function imageFrom(raw) {
  const fromMedia = firstUrl(raw.mediaContent) ?? firstUrl(raw.mediaThumbnail);
  if (fromMedia) return fromMedia;
  if (raw.enclosure?.url && /^image\//.test(raw.enclosure.type ?? 'image/')) return raw.enclosure.url;
  const html = raw['content:encoded'] ?? raw.content ?? '';
  const m = html.match(/<img[^>]+src=["']([^"']+)["']/i);
  return m ? m[1] : null;
}

function toIso(value) {
  if (!value) return null;
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? null : d.toISOString();
}

/** Turn one raw RSS item into our standard shape, or null if it is unusable. */
export function normalizeItem(raw, source) {
  let title = stripHtml(raw.title ?? '');
  const rawLink = raw.link ?? raw.guid;
  if (!title || !rawLink || !/^https?:/i.test(rawLink)) return null;

  let sourceName = source.name;
  let description = stripHtml(raw.contentSnippet ?? raw.summary ?? raw.content ?? '');

  if (source.aggregator) {
    // Google News titles look like "Headline - Outlet"; the outlet is also in <source>.
    const outlet = typeof raw.sourceTag === 'string' ? raw.sourceTag : raw.sourceTag?._;
    const m = title.match(/^(.*\S)\s+[-–—]\s+([^-–—]+)$/);
    if (m) {
      title = m[1].trim();
      sourceName = (outlet || m[2]).trim();
    } else if (outlet) {
      sourceName = outlet.trim();
    }
    // Some outlets repeat their own name in the headline too: "Story - Chicago Sun-Times".
    const tail = new RegExp(`\\s+[-–—|]\\s+${sourceName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`, 'i');
    title = title.replace(tail, '').trim();
    // Its description only repeats headlines, so it is not useful as a summary.
    description = '';
  }

  const fullText = stripHtml(raw['content:encoded'] ?? '');
  const link = canonicalUrl(rawLink);

  return {
    id: shortHash(link),
    title,
    link,
    sourceId: source.id,
    sourceName,
    trust: source.trust ?? 3,
    curated: Boolean(source.curated),
    neutralOk: Boolean(source.neutralOk),
    aggregator: Boolean(source.aggregator),
    category: source.category ?? 'general',
    region: source.region ?? null,
    publishedAt: toIso(raw.isoDate ?? raw.pubDate),
    description,
    fullText,
    image: imageFrom(raw),
  };
}
