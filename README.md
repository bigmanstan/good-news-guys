# Good News Guys

A daily digest of positive news, built as a static Astro site. It costs $0 to run and has no server or database.

## Project layout

```
config/          feed list (sources) and city list
pipeline/        Node script run daily by GitHub Actions
data/digests/    one JSON file per day (YYYY-MM-DD.json)
data/review/     uncertain stories awaiting approval
src/pages/       Astro routes
public/          static assets
```

Run locally with `npm run dev`. Build with `npm run build`.

## Running the pipeline

| Command | What it does |
| --- | --- |
| `npm run digest:dry` | Fetch and score everything, print the digest and review queue, write nothing |
| `npm run digest` | Write `data/digests/<today>.json` and `data/review/<today>.json` |
| `npm run review:apply` | Apply `approve` / `reject` decisions from review files to their digests |
| `npm test` | Unit tests for the pipeline |

Extra flags for `node pipeline/run.js`: `--date YYYY-MM-DD`, `--no-local`, `--only gnn,nasa`, `--scheduled` (exit early unless it's 6 AM or later in Toronto and today's digest doesn't exist yet).

**Tuning.** All knobs are in `config/`: feeds in `sources.json`, cities in `cities.json`, and the blocklist, skip patterns, word scores, thresholds and category keywords in `scoring.json`. A dry run prints what each rule filtered, so you can adjust and rerun.

**Pinning.** Set `"pinned": true` on a story in that day's digest file. Pinned stories always go to the top 3.

**GitHub setup.** In the repo's Settings → Actions → General, set workflow permissions to "Read and write" and tick "Allow GitHub Actions to create and approve pull requests". Otherwise the daily job can't push the digest or open the review PR.

**Deploy.** Cloudflare Pages is connected to this repo (preset Astro, build `npm run build`, output `dist`, `NODE_VERSION=22`). Every push to `main`, including the bot's daily digest commit, rebuilds the site.

## How a day runs

1. **6 AM Toronto time**: a GitHub Actions workflow starts a Node script.
2. **Fetch**: the script pulls about 30–50 RSS feeds and normalizes each item (title, link, source, date, description, image).
3. **Filter**: all rules are plain code, with no LLM or outside service. Skip patterns drop sports results, spam and listicles, and a word blocklist drops obvious negatives. Items that arrive with only a headline get their description from the article page's own meta tags, and the blocklist runs again on that text. A word-list scorer (the AFINN sentiment lexicon plus our own weights in `scoring.json`) then rates each remaining story positive, neutral or negative, with a confidence score. Confident positives go into the digest. Uncertain ones go to the review queue, never straight to the site.
4. **Dedupe**: stories with very similar titles (by word overlap) are merged. The most reputable source becomes the main link, and the others are listed under "also covered by."
5. **Summarize**: each story gets a 2–3 sentence summary, a category, a read time and a source credit.
6. **Publish**: the day is saved as `data/digests/YYYY-MM-DD.json` and committed, which triggers a Cloudflare Pages rebuild.

## Data model

- **Story**: id, headline, summary, source name, source link, category, region (for local), positivity score, read time, publish date, pinned.
- **Digest**: date, top 3 story ids, full story list.
- **Sources**: a config file of feeds, each with a category, region and trust level. Adding a feed means editing one line.

## Pages

| Route | Purpose |
| --- | --- |
| `/` | Today's digest with the Top 3 hero |
| `/category/[slug]` | Category pages (science, etc.) |
| `/local/[city]` | City pages |
| `/story/[id]` | One page per story (SEO) |
| `/archive/[date]` | Past digests |

Only bookmarks, the light/dark theme and the saved city run in the browser, all kept in the browser's own storage.

## Local news without tracking

Each city in `config/cities.json` gets its stories from local outlets' own RSS feeds (entries in `config/sources.json` with a matching `region`), which include real descriptions. Google News search can be switched on per city with `googleNews: true` as a fallback. It only provides headlines, so those stories always go to review. Readers pick a city, or allow a one-time location lookup that maps to the nearest listed city. Only the city name is saved, and only in the reader's browser.

## Admin and review

Each morning the pipeline opens a GitHub pull request listing uncertain stories. Approve or reject them by editing a small file, or later through a simple admin page that uses GitHub login. Pinning a story means flipping its `pinned` flag. Git provides version history and an audit trail.

## Why this design

- $0 to run, and with no server or database there's little to break or attack.
- Fast, SEO-friendly pages that are easy to cache for a PWA.
- Every digest is a file in the repo, so the archive and its history come built in.

## Later

- **Accounts, synced bookmarks, "My Feed"**: Supabase free tier (auth plus database).
- **Email digests**: one more step at the end of the daily job, using Brevo or Resend's free tier.
- **Push notifications**: Web Push through a Cloudflare Worker.
- **Review queue**: move it into Supabase if GitHub-based reviews get unwieldy.
