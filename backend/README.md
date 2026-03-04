# putivnyk-api

Backend API for **Путівник** — Kyiv walking tour guide.

## Endpoints

| Method | Path | Query params | Description |
|--------|------|--------------|-------------|
| GET | `/` | — | Service info |
| GET | `/health` | — | Health check |
| GET | `/events` | `city` (default: kyiv), `lang` (uk / en) | Kyiv events list |

## Local development

```bash
npm install
npm run dev
```

Server starts on `http://localhost:3000`.

## Deploy on Render

1. Push to GitHub
2. Create **New Web Service** on [render.com](https://render.com)
3. Connect your repo
4. Render auto-detects Node.js → `npm install` + `npm start`
5. Your API is live at `https://putivnyk-api.onrender.com`
