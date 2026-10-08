// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

import { createReadStream, existsSync } from 'node:fs';
import { resolve } from 'node:path';
import { defineConfig, type Plugin } from 'vite';
import react from '@vitejs/plugin-react';

// Atelier SPA, built to modules/web/dist, uploaded to the private S3
// bucket and served through CloudFront OAC.
//
// No endpoint is baked into the bundle. The app fetches /config.json at boot
// (written post-deploy by infra/scripts/deploy.sh), so the same bundle runs in
// every environment. STEP files are fetched from the presigned S3 URLs the query
// service returns in parts[].cadUrl, never copied into dist.

const CAD_DIR = resolve(__dirname, '../cad/stp');

/** Dev server only: serves /config.json, and /cad/<product>/<file>.stp from modules/cad/stp as the fixtures' cadUrl target. */
function localSite(): Plugin {
  return {
    name: 'atelier-local-site',
    apply: 'serve',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const url = (req.url ?? '').split('?')[0];
        if (url === '/config.json') {
          res.setHeader('content-type', 'application/json');
          res.end(JSON.stringify({ envName: 'local', apiBase: '/api', plms: ['fr', 'de', 'uk', 'es'] }));
          return;
        }
        const cad = url.match(/^\/cad\/((?:[a-z0-9-]+\/)?[a-z0-9-]+\.stp)$/);
        if (cad) {
          const file = resolve(CAD_DIR, cad[1]);
          if (!existsSync(file)) {
            res.statusCode = 404;
            res.end();
            return;
          }
          res.setHeader('content-type', 'application/step');
          createReadStream(file).pipe(res);
          return;
        }
        next();
      });
    },
  };
}

export default defineConfig({
  plugins: [react(), localSite()],
  worker: { format: 'es' },
  // occt-import-js is imported only by the STEP worker, which the dependency scan does not crawl: without it here the
  // dev server finds it on the first STEP load and reloads the page mid-session.
  optimizeDeps: { include: ['occt-import-js'] },
  build: {
    outDir: 'dist',
    // Hashed asset filenames are what make the CloudFront cache safe.
    sourcemap: false,
    chunkSizeWarningLimit: 900,
  },
  server: {
    port: 5173,
  },
});
