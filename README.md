# CodeCollab

A real-time collaborative coding platform where multiple users can join a room, write code together, compile it, and communicate via video calls.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React 18, React Router v6, CodeMirror 5 |
| Backend | Java 17, Spring Boot 3.2, WebSocket |
| Real-Time | Native WebSocket |
| Video Calling | WebRTC (peer-to-peer) |
| Code Execution | [Piston API](https://github.com/engineer-man/piston) |

## Features

- **Real-time collaboration** — Multiple users edit code simultaneously with live sync
- **Room system** — Create or join rooms via unique Room IDs
- **Multi-language support** — Python, Java, C++, C
- **Code compilation** — Run code directly in the browser with stdin input
- **Video/Audio calls** — WebRTC-based peer-to-peer calls within the room
- **Connected users** — See who's in the room with avatars
- **Responsive UI** — Works on desktop, tablet, and mobile
- **Modern dark theme** — Dracula-inspired color palette

## Project Structure

```
CodeCollab/
├── frontend/              # React app (deploy on Vercel)
│   ├── public/
│   ├── src/
│   │   ├── components/    # Editor, Client
│   │   ├── pages/         # Home, EditorPage
│   │   ├── service/       # WebRTC PeerService
│   │   ├── socket.js      # WebSocket wrapper
│   │   └── Actions.js     # Event constants
│   ├── package.json
│   └── vercel.json
├── backend/               # Spring Boot app (deploy on Render)
│   ├── src/main/java/com/codecollab/
│   │   ├── config/        # WebSocket & CORS config
│   │   ├── handler/       # WebSocket message handler
│   │   ├── service/       # Room management & compiler
│   │   ├── controller/    # REST API
│   │   └── model/         # Data models
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
git clone https://github.com/Aman4017/CollabCode.git
cd CollabCode
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

### 4. Verify

| Check | URL |
|-------|-----|
| Backend health | `http://localhost:8080/api/health` |
| Frontend | `http://localhost:3000` |

## Deployment

### Frontend → Vercel (free)

1. Import the repo on [vercel.com](https://vercel.com)
2. Set **Root Directory** to `frontend`
3. Add environment variable: `REACT_APP_BACKEND_URL` = your backend URL
4. Deploy

### Backend → Render (free)

1. Create a **Web Service** on [render.com](https://render.com)
2. Connect your GitHub repo
3. Set **Root Directory** to `backend`, **Runtime** to Docker
4. Add environment variables:
   - `PORT` = `8080`
   - `CORS_ALLOWED_ORIGINS` = your Vercel frontend URL
5. Deploy

## Language Support

| Language | Syntax Highlighting | Compilation |
|----------|-------------------|-------------|
| Python   | ✓                 | ✓           |
| Java     | ✓                 | ✓           |
| C++      | ✓                 | ✓           |
| C        | ✓                 | ✓           |
