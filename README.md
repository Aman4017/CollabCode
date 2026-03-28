# CodeCollab

A real-time collaborative coding platform where multiple users can join a room, write code together, compile it, and communicate via video calls.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React 18, React Router v6, CodeMirror 5 |
| Backend | Java 17, Spring Boot 3.2 |
| Real-Time | WebSocket (native) |
| Video Calling | WebRTC (peer-to-peer) |
| Code Execution | [Piston API](https://github.com/engineer-man/piston) |

## Features

- **Real-time collaboration** — Multiple users edit code simultaneously with live sync
- **Room system** — Create or join rooms via unique Room IDs
- **Multi-language support** — Python, Java, C++, C
- **Code compilation** — Run code directly in the browser with stdin input
- **Video/Audio calls** — WebRTC-based peer-to-peer calls within the room
- **Connected users** — See who's in the room with avatars

## Project Structure

```
CodeCollab/
├── frontend/          # React app (deploy on Vercel)
│   ├── public/
│   ├── src/
│   ├── package.json
│   └── vercel.json
├── backend/           # Spring Boot app (deploy on Render)
│   ├── src/main/java/com/codecollab/
│   ├── pom.xml
│   └── Dockerfile
└── README.md
```

## Local Development

### Prerequisites

- **Node.js** 16+ and npm
- **Java** 17+
- **Maven** 3.8+

### 1. Clone the repo

```bash
git clone https://github.com/Aman4017/CodeCollab.git
cd CodeCollab
```

### 2. Start the backend

```bash
cd backend
mvn spring-boot:run
```

The backend starts on `http://localhost:8080`.

### 3. Start the frontend

```bash
cd frontend
npm install
npm start
```

The frontend starts on `http://localhost:3000`.

## Deployment

### Frontend → Vercel

1. Push the repo to GitHub
2. Go to [vercel.com](https://vercel.com) and import the repo
3. Set the **Root Directory** to `frontend`
4. Add the environment variable:
   - `REACT_APP_BACKEND_URL` = your deployed backend URL (e.g., `https://codecollab-backend.onrender.com`)
5. Deploy

### Backend → Render

1. Go to [render.com](https://render.com) and create a new **Web Service**
2. Connect your GitHub repo
3. Set:
   - **Root Directory**: `backend`
   - **Runtime**: Docker
   - **Environment Variables**:
     - `PORT` = `8080`
     - `CORS_ALLOWED_ORIGINS` = your Vercel frontend URL (e.g., `https://codecollab.vercel.app`)
4. Deploy

## Language Support

| Language | Syntax Highlighting | Compilation |
|----------|-------------------|-------------|
| Python   | ✓                 | ✓           |
| Java     | ✓                 | ✓           |
| C++      | ✓                 | ✓           |
| C        | ✓                 | ✓           |
