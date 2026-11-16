import Parser from 'rss-parser';

const parser = new Parser({
  timeout: 15000,
  headers: { 'User-Agent': 'GoodNewsGuysBot/0.1 (daily RSS digest)' },
  customFields: {
    item: [
      ['media:content', 'mediaContent', { keepArray: true }],
      ['media:thumbnail', 'mediaThumbnail', { keepArray: true }],
      ['source', 'sourceTag'],
    ],
  },
});

/** Google News search feed for one city, limited to the last day. */
export function cityFeed(city) {
  const q = encodeURIComponent(`"${city.name}" when:1d`);
  const gl = city.country;
  const hl = `${city.lang}-${gl}`;
  return {
    id: `local-${city.slug}`,
    name: 'Google News',
    url: `https://news.google.com/rss/search?q=${q}&hl=${hl}&gl=${gl}&ceid=${gl}:${city.lang}`,
    category: 'local',
    region: city.slug,
    trust: 2,
    curated: false,
    aggregator: true,
  };
}

/**
 * Fetch every feed with limited concurrency. One broken feed never stops the run.
 * Returns [{ source, items }] plus a list of { source, error }.
 */
export async function fetchAll(sources, { concurrency = 6 } = {}) {
  const results = [];
  const errors = [];
  let next = 0;

  async function worker() {
    while (next < sources.length) {
      const source = sources[next++];
      try {
        const feed = await parser.parseURL(source.url);
        results.push({ source, items: feed.items ?? [] });
      } catch (err) {
        errors.push({ source, error: err.message });
      }
    }
  }

  await Promise.all(Array.from({ length: Math.min(concurrency, sources.length) }, worker));
  return { results, errors };
}
