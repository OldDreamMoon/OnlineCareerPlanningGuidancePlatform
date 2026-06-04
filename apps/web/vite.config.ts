import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

function matchesNodeModulePackage(id: string, packageName: string) {
  return id.includes(`/node_modules/${packageName}/`) || id.includes(`\\node_modules\\${packageName}\\`);
}

function matchesAnyNodeModulePackage(id: string, packageNames: string[]) {
  return packageNames.some((packageName) => matchesNodeModulePackage(id, packageName));
}

export default defineConfig({
  envDir: "../..",
  plugins: [react()],
  server: {
    host: "0.0.0.0",
    port: 5173,
    proxy: {
      "/api/v1": {
        target: "http://127.0.0.1:8080",
        changeOrigin: true,
      },
      "/ws/notifications": {
        target: "ws://127.0.0.1:8080",
        changeOrigin: true,
        ws: true,
      },
      "/ai/interview/live": {
        target: "http://127.0.0.1:8765",
        changeOrigin: true,
        ws: true,
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes("node_modules")) {
            return;
          }

          if (matchesAnyNodeModulePackage(id, ["react", "react-dom", "scheduler", "react-router", "react-router-dom"])) {
            return "react-vendor";
          }

          if (matchesAnyNodeModulePackage(id, ["@ant-design/icons", "lucide-react"])) {
            return "icon-vendor";
          }

          if (matchesAnyNodeModulePackage(id, ["framer-motion", "motion-dom", "motion-utils"])) {
            return "motion-vendor";
          }
        },
      },
    },
  },
});
