import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// base './' keeps every asset path relative, so the built site works from any folder or domain.
export default defineConfig({
  base: './',
  plugins: [react()],
  // PRIVACY.md and LICENSE come from the repo root, one level up.
  server: { fs: { allow: ['..'] } },
});
