# Mode website

The landing page for Mode, as a React app (Vite + React + TypeScript).

```sh
npm install
npm run dev       # local preview with hot reload
npm run build     # static site in dist/, deployable anywhere (all paths are relative)
```

## Deploy

The site runs on Cloudflare (Workers static assets) at https://heymode.app. `wrangler.jsonc` has the setup.

```sh
npx wrangler login   # once per computer
npm run deploy       # builds, uploads dist/, keeps heymode.app pointed at it
```

`public/_headers` sets the caching: hashed JS/CSS forever, the film and screens for a day.

Downloads: `worker/index.js` serves `heymode.app/download/mac|windows|windows-msi|linux|linux-deb` straight from
heymode.app (it fetches the newest GitHub release's file and sends it as a download, cached by Cloudflare), and
`heymode.app/download` picks the right one for the visitor's computer. Until a release is published, those links
show a short "almost here" page. The site's download buttons pick the visitor's platform the same way
(`src/lib/platform.ts`).

## Where things are

| Path | What it is |
|---|---|
| `src/App.tsx` | The page, top to bottom |
| `src/components/` | Top bar, hero, the film (plays only while it's on screen), Mo, icons |
| `src/tour/chapters.tsx` | The feature tour as data: each chapter's words, its screens (one per scroll) and the card beside the phone |
| `src/tour/Tour.tsx` | Draws the pinned tour: chapter list, words, the iPhone, the close-up card |
| `src/tour/engine.ts` | Plays the tour: one scroll = one step, and marks the current chapter and step for the CSS |
| `src/sections/` | Download Mode Setup, the small things, How it works, Try saying, Questions, closing |
| `src/styles.css` | All styles, light and dark |
| `public/screens/` | The tour's phone screens: boards from the "Mode · Mo theme · all screens" canvas, rendered at 2× |
| `public/story.mp4`, `public/story-vertical.mp4` | The films, copied in from `docs/media/` before each build (`npm run films`); not committed here |

Each screen in a chapter is one scroll. To change a chapter, edit its `screens` and `detail` in `src/tour/chapters.tsx`;
in the card, `.a2`/`.b2` show or hide things from the second step on, and `.h1`–`.h4` highlight on that step.
If a board changes on the canvas, re-render it to a 390×844 PNG at 2× and save it as `public/screens/<Board>.webp`
(`cwebp -q 80 -m 6 Board.png -o public/screens/Board.webp`). Screens load one chapter ahead, not all at once.
