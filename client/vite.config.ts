import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Vite-Konfiguration fuer den Lehr-Client.
//
// Der Dev-Server (Port 5173) leitet API- und Actuator-Aufrufe per Proxy an den
// Spring-Boot-Server (Port 8080) weiter. Dadurch laeuft alles same-origin: der
// Browser gibt den 'Server-Timing'-Header ohne CORS-Sonderregeln frei, und wir
// koennen im Code relative URLs (/api/...) verwenden.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/actuator': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
});
