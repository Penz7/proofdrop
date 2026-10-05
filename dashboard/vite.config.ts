import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

const backend = process.env.BACKEND_URL ?? "http://localhost:3000";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  build: {
    // MapLibre alone is ~1 MB; keep it in its own long-cached chunk.
    chunkSizeWarningLimit: 1100,
    rollupOptions: {
      output: {
        manualChunks: { maplibre: ["maplibre-gl"], vendor: ["react", "react-dom", "react-router", "@tanstack/react-query"] },
      },
    },
  },
  server: {
    port: 5173,
    proxy: {
      "/api": { target: backend, changeOrigin: true },
      "/fleet": { target: backend.replace(/^http/, "ws"), ws: true, changeOrigin: true },
    },
  },
});
