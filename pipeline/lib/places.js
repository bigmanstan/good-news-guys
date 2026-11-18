function escapeRegex(s) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/** Every name a city goes by: its own name, aliases, and its neighbourhoods and nearby towns. */
export function cityNames(city) {
  return [city.name, ...(city.aliases ?? []), ...(city.areas ?? []).flatMap(([name, , , ...other]) => [name, ...other])];
}

/** Whole-word, case-insensitive matcher for a list of names. */
export function namesRegex(names, flags = 'i') {
  // Longest first, so "North Vancouver" wins over "Vancouver".
  const sorted = [...new Set(names)].sort((a, b) => b.length - a.length);
  return new RegExp(`(?<![\\w'])(${sorted.map(escapeRegex).join('|')})(?![\\w])`, flags);
}

/**
 * Finds which neighbourhoods or nearby towns a local story mentions, so the app can
 * show what's happening closest to the reader. Only looks within the story's own city.
 */
export function createPlaceFinder(cities) {
  const byRegion = new Map();
  for (const city of cities) {
    const lookup = new Map();
    for (const [name, lat, lon, ...other] of city.areas ?? []) {
      for (const n of [name, ...other]) lookup.set(n.toLowerCase(), { name, lat, lon });
    }
    if (lookup.size) byRegion.set(city.slug, { lookup, re: namesRegex([...lookup.keys()], 'gi') });
  }

  /** Up to `max` places, in the order the story mentions them. */
  return function findPlaces(story, max = 3) {
    const entry = story.region && byRegion.get(story.region);
    if (!entry) return [];
    const text = `${story.title} ${story.description ?? ''}`;
    const out = [];
    for (const m of text.matchAll(entry.re)) {
      const place = entry.lookup.get(m[1].toLowerCase());
      if (place && !out.some((p) => p.name === place.name)) out.push(place);
      if (out.length >= max) break;
    }
    return out;
  };
}
