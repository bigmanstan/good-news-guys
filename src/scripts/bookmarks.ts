// Bookmarks live only in this browser's localStorage. Every [data-bookmark]
// button on any page toggles through this one delegated listener, including
// buttons the Saved page renders after load.
const KEY = 'gng:bookmarks';

export type SavedStory = {
  id: string;
  headline: string;
  summary: string;
  sourceName: string;
  sourceLink: string;
  category: string;
  readTime: number;
  digestDate: string;
  region: string | null;
  savedAt?: string;
};

export function loadBookmarks(): SavedStory[] {
  try {
    const v = JSON.parse(localStorage.getItem(KEY) ?? '[]');
    return Array.isArray(v) ? v.filter((b) => b && typeof b.id === 'string') : [];
  } catch {
    return [];
  }
}

function save(list: SavedStory[]) {
  try {
    localStorage.setItem(KEY, JSON.stringify(list));
  } catch {}
}

export function syncBookmarkButtons() {
  const ids = new Set(loadBookmarks().map((b) => b.id));
  for (const btn of document.querySelectorAll<HTMLButtonElement>('[data-bookmark]')) {
    try {
      const { id } = JSON.parse(btn.dataset.bookmark ?? '{}');
      btn.setAttribute('aria-pressed', String(ids.has(id)));
    } catch {}
  }
}

let installed = false;
export function installBookmarks() {
  if (installed) return;
  installed = true;
  document.addEventListener('click', (e) => {
    const btn = (e.target as Element).closest<HTMLButtonElement>('[data-bookmark]');
    if (!btn) return;
    let story: SavedStory;
    try {
      story = JSON.parse(btn.dataset.bookmark ?? '');
    } catch {
      return;
    }
    const list = loadBookmarks();
    const i = list.findIndex((b) => b.id === story.id);
    if (i >= 0) list.splice(i, 1);
    else list.unshift({ ...story, savedAt: new Date().toISOString() });
    save(list);
    syncBookmarkButtons();
    document.dispatchEvent(new CustomEvent('gng:bookmarks'));
  });
  syncBookmarkButtons();
}
