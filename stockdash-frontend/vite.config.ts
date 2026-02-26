import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:18090',
      '/actuator': 'http://localhost:18090',
      '/v3': 'http://localhost:18090',
      '/swagger-ui': 'http://localhost:18090'
    }
  }
});
