import {onRequest} from 'firebase-functions/v2/https';
import handler from './src/main.js';

export const liquidApi = onRequest(
  {cors: true, timeoutSeconds: 120, memory: '512MiB', secrets: ['CLOUDINARY_CLOUD_NAME', 'CLOUDINARY_API_KEY', 'CLOUDINARY_API_SECRET']},
  async (request, response) => {
    const req = {
      headers: request.headers,
      bodyJson: typeof request.body === 'object' && request.body !== null ? request.body : {},
      bodyText: typeof request.body === 'string' ? request.body : JSON.stringify(request.body || {})
    };
    const res = {
      json(payload, status = 200) {
        if (!response.headersSent) response.status(status).json(payload);
        return response;
      }
    };
    await handler({req, res, error: console.error});
  }
);
