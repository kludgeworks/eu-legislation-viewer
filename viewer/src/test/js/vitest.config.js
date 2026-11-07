// SPDX-License-Identifier: MIT
// @ts-check
import { defineConfig } from "vitest/config";

// The tests of the page scripts. They run in happy-dom, a DOM without a browser.
//
// Under Bazel, the files are symlinks in the sandbox. Vite keeps the symlinked paths, so that it
// does not follow them out of the sandbox. Its cache goes to the test's own temporary directory,
// because the source tree is read-only.
export default defineConfig({
  cacheDir: process.env.TEST_TMPDIR ? `${process.env.TEST_TMPDIR}/vite` : undefined,
  resolve: { preserveSymlinks: true },
  test: {
    environment: "happy-dom",
    include: ["*.test.js"],
  },
});
