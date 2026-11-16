import citiesConfig from '../../config/cities.json';
import sourcesConfig from '../../config/sources.json';

export interface Story {
  id: string;
  headline: string;
  summary: string;
  sourceName: string;
  sourceLink: string;
  alsoCoveredBy: { name: string; link: string }[];
  category: string;
  region: string | null;
  positivity: number;
  readTime: number;
  publishedAt: string | null;
  digestDate: string;
  image: string | null;
  pinned: boolean;
}

export interface Digest {
  date: string;
  generatedAt: string;
  top3: string[];
  stories: Story[];
}

export interface City {
  slug: string;
  name: string;
  country: string;
  lat: number;
  lon: number;
}

// Every digest file is bundled at build time; the site has no runtime data source.
const files = import.meta.glob<Digest>('../../data/digests/*.json', { eager: true, import: 'default' });

export const digests: Digest[] = Object.values(files)
  .filter((d) => d && Array.isArray(d.stories))
  .sort((a, b) => b.date.localeCompare(a.date));

export const latest: Digest | undefined = digests[0];

export const cities: City[] = citiesConfig.cities;

export const sourceCount = sourcesConfig.feeds.filter((f) => (f as { enabled?: boolean }).enabled !== false).length;

export const CATEGORIES = [
  { slug: 'science', label: 'Science' },
  { slug: 'space', label: 'Space' },
  { slug: 'health', label: 'Health' },
  { slug: 'environment', label: 'Environment' },
  { slug: 'animals', label: 'Animals' },
  { slug: 'community', label: 'Community' },
  { slug: 'tech', label: 'Tech' },
  { slug: 'culture', label: 'Culture' },
  { slug: 'general', label: 'Uplifting' },
] as const;

export function categoryLabel(slug: string): string {
  return CATEGORIES.find((c) => c.slug === slug)?.label ?? slug.charAt(0).toUpperCase() + slug.slice(1);
}

export function cityName(slug: string | null): string | null {
  if (!slug) return null;
  return cities.find((c) => c.slug === slug)?.name ?? slug;
}

/** Top 3 first (in their order), then everything else as ranked by the pipeline. */
export function splitDigest(d: Digest) {
  const byId = new Map(d.stories.map((s) => [s.id, s]));
  const top = d.top3.map((id) => byId.get(id)).filter((s): s is Story => Boolean(s));
  const topIds = new Set(top.map((s) => s.id));
  const rest = d.stories.filter((s) => !topIds.has(s.id));
  return {
    top,
    global: rest.filter((s) => !s.region),
    local: rest.filter((s) => s.region),
  };
}

/** Stories from the most recent `days` digests that match, newest digest first. */
export function recentStories(match: (s: Story) => boolean, days = 7) {
  return digests.slice(0, days)
    .map((d) => ({ date: d.date, stories: d.stories.filter(match) }))
    .filter((g) => g.stories.length);
}

/** One page per story id; if a story somehow appears twice, the newest copy wins. */
export function allStories(): Story[] {
  const seen = new Map<string, Story>();
  for (const d of digests) for (const s of d.stories) if (!seen.has(s.id)) seen.set(s.id, s);
  return [...seen.values()];
}

const dayFormat = new Intl.DateTimeFormat('en-CA', { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric', timeZone: 'UTC' });
const shortFormat = new Intl.DateTimeFormat('en-CA', { weekday: 'short', month: 'short', day: 'numeric', timeZone: 'UTC' });

// Digest dates are plain YYYY-MM-DD in Toronto time; format them at UTC noon so no timezone shifts the day.
export function formatDate(date: string, short = false): string {
  return (short ? shortFormat : dayFormat).format(new Date(`${date}T12:00:00Z`));
}

/** "Today" / "Yesterday" relative to the newest digest, else the date. */
export function relativeDay(date: string): string {
  if (!latest) return formatDate(date);
  const diff = Math.round((Date.parse(`${latest.date}T12:00:00Z`) - Date.parse(`${date}T12:00:00Z`)) / 864e5);
  if (diff === 0) return 'Today';
  if (diff === 1) return 'Yesterday';
  return formatDate(date, true);
}

/** The fields a bookmark keeps, so saved stories render even after they leave the recent window. */
export function bookmarkPayload(s: Story): string {
  const { id, headline, summary, sourceName, sourceLink, category, readTime, digestDate, region } = s;
  return JSON.stringify({ id, headline, summary, sourceName, sourceLink, category, readTime, digestDate, region });
}
